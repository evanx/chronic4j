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
import java.security.KeyStore;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vellum.ssl.OpenHostnameVerifier;
import vellum.ssl.OpenTrustManager;
import vellum.ssl.SSLContexts;
import vellum.util.Streams;

/**
 *
 * @author evan.summers
 */
public class ChronicPoster {

    static Logger logger = LoggerFactory.getLogger(ChronicPoster.class);

    private final int maximumPostLength = 2000;
    private SSLContext sslContext;

    public ChronicPoster() {
    }

    public void init(KeyStore keyStore, char[] pass) throws GeneralSecurityException {
        init(SSLContexts.create(keyStore, pass, new OpenTrustManager()));
    }

    public void init(SSLContext sslContext) {
        this.sslContext = sslContext;
    }
    
    public String post(String urlString) throws IOException {
        return post(urlString, null);
    }

    public String post(String urlString, String string) throws IOException {
        logger.info("post {} {}", urlString, string);
        HttpsURLConnection connection;
        connection = (HttpsURLConnection) new URL(urlString).openConnection();
        connection.setSSLSocketFactory(sslContext.getSocketFactory());
        connection.setHostnameVerifier(new OpenHostnameVerifier());
        connection.setUseCaches(false);
        connection.setDoInput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "plain/text");
        if (string != null) {
            if (string.length() > maximumPostLength) {
                return "ERROR: length exceeded";
            }
            byte[] bytes = string.getBytes();
            logger.info("post {}", bytes.length);
            connection.setRequestProperty("Content-Length", Integer.toString(bytes.length));
            connection.setDoOutput(true);
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(bytes);
            }
        } else {
            connection.setDoOutput(false);
        }
        logger.info("responseCode {}", connection.getResponseCode());
        String response;
        try (InputStream inputStream = connection.getInputStream()) {
            response = Streams.readString(inputStream);
            logger.debug("chronica response {}", response);
        }
        connection.disconnect();
        return response.trim();
    }

}
