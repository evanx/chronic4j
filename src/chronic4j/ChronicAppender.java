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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javax.net.ssl.HttpsURLConnection;
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
import vellum.util.Streams;

/**
 *
 * @author evan.summers
 */
public class ChronicAppender extends AppenderSkeleton implements Runnable {

    static Logger logger = LoggerFactory.getLogger(ChronicAppender.class);

    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private final ArrayDeque<LoggingEvent> deque = new ArrayDeque();
    private long period = TimeUnit.SECONDS.toMillis(60);
    private String postAddress = "https://chronica.co/post";
    private final int maximumPostLength = 2000;
    private boolean initialized;
    private boolean running;
    private long taskTimestamp;
    private String keyStoreLocation = System.getProperty("user.home") + "/.chronica/etc/keystore.jks";
    private char[] sslPass = "chronica".toCharArray();
    SSLContext sslContext;
    ChronicProcessor processor = new DefaultProcessor();
    String topicLabel;
    
    public ChronicAppender() {
    }

    public ChronicAppender(String postAddress) {
        this.postAddress = postAddress;
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
        logger.info("initialize: postAddress {}", postAddress);
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
        logger.info("initialize: topic {}, processor {}", topicLabel, processor.getClass());
        try {
            sslContext = SSLContexts.create(keyStoreLocation, sslPass, new OpenTrustManager());
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

    @Override
    public void run() {
        logger.info("run {} {}", deque.size(), postAddress);
        taskTimestamp = System.currentTimeMillis();
        Deque<LoggingEvent> snapshot;
        synchronized (deque) {
            snapshot = deque.clone();
            deque.clear();
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
                    event.getLoggerName(),
                    event.getMDC("method"),
                    event.getMessage().toString());
            if (builder.length() + formattedString.length() + 1 >= maximumPostLength) {
                break;
            }
            builder.append(formattedString);
            builder.append("\n");
        }
        post(builder.toString());
    }

    private void post(String string) {
        HttpsURLConnection connection;
        try {
            byte[] bytes = string.getBytes();
            logger.trace("post: {}", bytes.length);
            URL url = new URL(postAddress);
            connection = (HttpsURLConnection) url.openConnection();
            connection.setSSLSocketFactory(sslContext.getSocketFactory());
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "plain/text");			
            connection.setRequestProperty("Content-Length", Integer.toString(bytes.length));
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(bytes);
            }
            try (InputStream inputStream = connection.getInputStream()) {
                logger.debug("chronica response {}", Streams.readString(inputStream));
            }
            connection.disconnect();
        } catch (IOException e) {
            logger.warn("post", e);
        } finally {
        }
    }
}
