/*
 Source https://code.google.com/p/vellum by @evanxsummers

 Licensed to the Apache Software Foundation (ASF) under one
 or more contributor license agreements. See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership. The ASF licenses this file to
 you under the Apache License, Version 2.0 (the "License").
 You may not use this file except in compliance with the
 License. You may obtain a copy of the License at:

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.  
 */
package chronic4j;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Priority;
import org.apache.log4j.spi.LoggingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vellum.data.Millis;
import vellum.exception.ParseException;
import vellum.format.CalendarFormats;
import vellum.format.Delimiters;
import vellum.ssl.OpenTrustManager;
import vellum.ssl.SSLContexts;
import vellum.util.Args;

/**
 *
 * @author evan.summers
 */
public class ChronicAppender extends AppenderSkeleton implements Runnable {

    static Logger logger = LoggerFactory.getLogger(ChronicAppender.class);

    private String resolveUrl = "https://secure.chronica.co/resolve";
    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private final ArrayDeque<LoggingEvent> deque = new ArrayDeque();
    private long period = TimeUnit.SECONDS.toMillis(60);
    private String postUrl;
    private final int maximumPostLength = 2000;
    private boolean initialized;
    private boolean running;
    private long taskTimestamp;
    private String keyStoreLocation = System.getProperty("user.home") + "/.chronica/etc/keystore.jks";
    private char[] sslPass = "chronica".toCharArray();
    SSLContext sslContext;
    ChronicProcessor processor = new DefaultProcessor();
    ChronicPoster poster = new ChronicPoster();
    String topicLabel;

    public ChronicAppender() {
    }

    public void setResolveUrl(String resolveUrl) {
        this.resolveUrl = resolveUrl;
    }
    
    public void setKeyStore(String keyStore) {
        this.keyStoreLocation = keyStore;
    }

    public void setPeriod(String period) {
        try {
            this.period = Millis.parse(period);
        } catch (ParseException e) {
            logger.error("Invalid period: {}", period);
        }
    }

    public void setPass(String pass) {
        this.sslPass = pass.toCharArray();
    }

    public void setProcessorClass(String className) {
        try {
            processor = (ChronicProcessor) Class.forName(className).newInstance();
            if (topicLabel == null) {
                topicLabel = processor.getClass().getSimpleName();
            }
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            logger.warn("Invalid processor class: {}", className);
            processor = null;
        }
    }

    public void setTopicLabel(String topicLabel) {
        this.topicLabel = topicLabel;
    }

    @Override
    protected void append(LoggingEvent le) {
        if (le.getLoggerName().equals(logger.getName())) {
        }
        if (!initialized) {
            initialized = true;
            initialize();
        }
        if (running && le.getLevel().toInt() >= Priority.DEBUG_INT) {
            if (taskTimestamp > 0 && System.currentTimeMillis() - taskTimestamp > period * 2) {
                running = false;
                synchronized (deque) {
                    deque.clear();
                }
            } else {
                synchronized (deque) {
                    deque.add(le);
                }
                processor.process(le);
            }
        }
    }

    private void initialize() {
        if (processor == null) {
            logger.error("Require processor class parameter: processorClass");
            return;
        }
        if (keyStoreLocation == null || sslPass == null) {
            logger.error("Require parameters for SSL connection: keyStore, pass");
            return;
        }
        if (topicLabel == null) {
            topicLabel = processor.getClass().getSimpleName();
        }
        logger.info("initialize {} {}", topicLabel, processor.getClass().getName());
        try {
            sslContext = SSLContexts.create(keyStoreLocation, sslPass, new OpenTrustManager());
            poster.init(sslContext);
            running = true;
            long initialDelay = period;
            scheduledExecutorService.scheduleAtFixedRate(this, initialDelay, period, TimeUnit.MILLISECONDS);
        } catch (IOException | GeneralSecurityException e) {
            logger.error("intialized", e);
        }
    }

    @Override
    public void close() {
        running = false;
        scheduledExecutorService.shutdown();
    }

    @Override
    public boolean requiresLayout() {
        return false;
    }

    Deque<LoggingEvent> snapshot;

    @Override
    public void run() {
        logger.info("run {}", deque.size());
        taskTimestamp = System.currentTimeMillis();
        synchronized (deque) {
            snapshot = deque.clone();
            deque.clear();
        }
        try {
            post();
        } catch (Throwable e) {
            logger.error("run", e);
        }
    }

    private void post() {
        if (postUrl == null) {
            if (!resolve()) {
                return;
            }
        }
        StringBuilder builder = new StringBuilder();
        String report = processor.buildReport();
        if (!report.startsWith("Topic: ")) {
            builder.append(String.format("Topic: %s\n", topicLabel));
        }
        builder.append(report);
        builder.append(String.format("INFO: event snapshot size: %d\n", snapshot.size()));
        builder.append("INFO:-\n");
        builder.append("Latest events:\n");
        while (snapshot.peek() != null) {
            LoggingEvent event = snapshot.poll();
            String formattedString = Args.formatDelimiterSquash(Delimiters.SPACE,
                    CalendarFormats.timestampFormat.format(TimeZone.getDefault(), event.getTimeStamp()),
                    event.getLevel().toString(),
                    event.getLoggerName());
            if (builder.length() + formattedString.length() + 1 >= maximumPostLength) {
                break;
            }
            builder.append(formattedString);
            builder.append("\n");
        }
        try {
            poster.post(postUrl, builder.toString());
        } catch (IOException e) {
            logger.warn(postUrl, e);
        }
    }

    public boolean resolve() {
        if (resolveUrl.equals("https://localhost:8444/resolve")) {
            postUrl = "https://localhost:8444/post";
            return true;
        }
        try {
            String response = poster.post(resolveUrl);
            if (response == null || response.startsWith("ERROR")) {
                logger.error("resolve {}", response);
                return false;
            } else {
                postUrl = String.format("https://%s/post", response);
                logger.info("resolve {}", postUrl);
                return true;
            }
        } catch (IOException e) {
            logger.warn(resolveUrl, e);
            return false;
        }
    }
    
    public String getPostUrl() {
        return postUrl;
    }       
}
