
/*
 * Source https://github.com/evanx by @evanxsummers

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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.net.HttpURLConnection;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import vellum.httpserver.HttpsServerProperties;
import vellum.httpserver.VellumHttpsServer;
import vellum.ssl.SSLContexts;
import vellum.util.Streams;


/**
 *
 * @author evans
 */
public class TestAppender implements HttpHandler {
    String keyStoreLocation = System.getProperty("user.home") + "/.chronica/etc/keystore.jks";
    char[] sslPass = "chronica".toCharArray();
    String response;
    String request;
    String path; 

    static {
        BasicConfigurator.configure(new ConsoleAppender(new PatternLayout("%d{ISO8601} %p [%c{1}] %m%n")));
    }
    
    public TestAppender() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void test() throws Exception {
        VellumHttpsServer server = new VellumHttpsServer();
        server.start(new HttpsServerProperties(8444, false, true), 
                SSLContexts.create(keyStoreLocation, sslPass),
                this);
        ChronicAppender appender = new ChronicAppender();
        path = "/resolve";
        response = "localhost:8444\n";
        appender.setResolveUrl("https://localhost:8444/resolve");
        Logger.getRootLogger().addAppender(appender);
        Logger logger = Logger.getLogger(TestAppender.class);
        logger.warn("test");
        path = "/post";
        response = "OK:\n";
        appender.run();
        Assert.assertTrue(request.startsWith("Topic: chronic4j appender"));
        server.shutdown();
    }

    @Override
    public void handle(HttpExchange he) throws IOException {
        Assert.assertEquals(path, he.getRequestURI().getPath());
        request = Streams.readString(he.getRequestBody());
        byte[] responseBytes = response.getBytes();
        he.sendResponseHeaders(HttpURLConnection.HTTP_OK,
                responseBytes.length);
        he.getResponseHeaders().set("Content-type", "text/plain");
        he.getResponseBody().write(responseBytes);
        he.close();
    }
}
