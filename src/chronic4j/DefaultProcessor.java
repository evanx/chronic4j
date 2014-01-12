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

import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Priority;
import org.apache.log4j.spi.LoggingEvent;
import vellum.util.Strings;

/**
 *
 * @author evan.summers
 */
public class DefaultProcessor implements ChronicProcessor {
    int errorCount;
    int warnCount;
    int infoCount;
    int debugCount;
    
    @Override
    public void process(LoggingEvent le) {
        if (le.getLevel().toInt() == Priority.ERROR_INT) {
            errorCount++;
        } else if (le.getLevel().toInt() == Priority.WARN_INT) {
            warnCount++;
        } else if (le.getLevel().toInt() == Priority.WARN_INT) {
            warnCount++;
        } else if (le.getLevel().toInt() == Priority.INFO_INT) {
            infoCount++;
        } else if (le.getLevel().toInt() == Priority.DEBUG_INT) {
            debugCount++;
        }        
    }
    
    @Override
    public String buildReport() {
        List<String> list = new ArrayList();
        list.add("Topic: chronic4j appender");
        list.add("Alert: NEVER");
        list.add(String.format("Value: error %d", errorCount));
        list.add(String.format("Value: warn %d", warnCount));
        list.add(String.format("Value: info %d", infoCount));
        list.add(String.format("Value: debug %d", debugCount));
        reset();
        return Strings.join("\n", list);
    }

    public void reset() {
        errorCount = 0;
        warnCount = 0;
        infoCount = 0;
        debugCount = 0;        
    }    
}
