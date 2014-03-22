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
import java.util.Map;
import vellum.util.Strings;

/**
 *
 * @author evan.summers
 */
public class ChronicMonitors {

    public static String buildReport(Map map) {
        List<String> list = new ArrayList();
        list.add("Topic: chronic4j appender");
        list.add("Alert: NEVER");
        for (Object key : map.keySet()) {
            Object value = map.get(key);
            list.add(String.format("Value: %s %s", key, value));
        }
        return Strings.join("\n", list);
    }
}
