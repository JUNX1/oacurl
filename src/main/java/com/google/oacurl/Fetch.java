// Copyright 2010 Google, Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.oacurl;

import java.io.FileNotFoundException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.oauth.OAuth;
import net.oauth.OAuthAccessor;
import net.oauth.OAuthConsumer;
import net.oauth.OAuthMessage;
import net.oauth.OAuthProblemException;
import net.oauth.OAuthServiceProvider;
import net.oauth.ParameterStyle;
import net.oauth.client.OAuthClient;
import net.oauth.client.OAuthResponseMessage;
import net.oauth.client.httpclient4.HttpClient4;
import net.oauth.http.HttpMessage;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;

import com.google.oacurl.dao.AccessorDao;
import com.google.oacurl.dao.ConsumerDao;
import com.google.oacurl.dao.ServiceProviderDao;
import com.google.oacurl.options.FetchOptions;
import com.google.oacurl.options.FetchOptions.Method;
import com.google.oacurl.util.LoggingConfig;
import com.google.oacurl.util.OAuthUtil;
import com.google.oacurl.util.PropertiesProvider;

/**
 * Main class for curl-like interactions authenticated by OAuth.
 * <p>
 * Assumes that the user has run {@link Login} to save the OAuth access
 * token to a local properties file.
 *
 * @author phopkins@google.com
 */
public class Fetch {
  private static Logger logger = Logger.getLogger(Fetch.class.getName());

  public static void main(String[] args) throws Exception {
    FetchOptions options = new FetchOptions();
    CommandLine line = options.parse(args);
    args = line.getArgs();

    if (options.isHelp()) {
      new HelpFormatter().printHelp("url", options.getOptions());
      System.exit(0);
    }

    if (args.length != 1) {
      new HelpFormatter().printHelp("url", options.getOptions());
      System.exit(-1);
    }

    LoggingConfig.init(options.isVerbose());

    String url = args[0];

    ServiceProviderDao serviceProviderDao = new ServiceProviderDao();
    ConsumerDao consumerDao = new ConsumerDao();
    AccessorDao accessorDao = new AccessorDao();

    Properties loginProperties = null;
    try {
      loginProperties = new PropertiesProvider(options.getLoginFileName()).get();
    } catch (FileNotFoundException e) {
      System.err.println(".gocurl.properties file not found in homedir");
      System.err.println("Make sure you've run ocurl-login first!");
      System.exit(-1);
    }

    OAuthServiceProvider serviceProvider = serviceProviderDao.nullServiceProvider();
    OAuthConsumer consumer = consumerDao.loadConsumer(loginProperties, serviceProvider);
    OAuthAccessor accessor = accessorDao.loadAccessor(loginProperties, consumer);

    OAuthClient client = new OAuthClient(new HttpClient4());

    try {
      OAuthMessage request;

      Method method = options.getMethod();
      if (method == Method.POST || method == Method.PUT){
        request = accessor.newRequestMessage(method.toString(),
            url,
            null,
            System.in);
        request.getHeaders().add(new OAuth.Parameter("Content-Type", options.getContentType()));
      } else {
        request = accessor.newRequestMessage(method.toString(),
            url,
            null,
            null);
      }

      request.getHeaders().addAll(options.getHeaders());

      logger.log(Level.INFO, "Fetching request with parameters: " + request.getParameters());

      OAuthResponseMessage response = client.access(request, ParameterStyle.AUTHORIZATION_HEADER);

      if (options.isInclude()) {
        System.out.print(response.getDump().get(HttpMessage.RESPONSE));
      }

      System.out.println(response.readBodyAsString());        
    } catch (OAuthProblemException e) {
      OAuthUtil.printOAuthProblemException(e);
    }
  }
}