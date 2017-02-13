package com.temenos.useragent.example.swagger.servlet;

/*
 * #%L
 * interaction-example-hateoas-simple
 * %%
 * Copyright (C) 2012 - 2014 Temenos Holdings N.V.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map.Entry;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.json.JsonWriter;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.util.StringUtils;

public class SwaggerServlet extends HttpServlet {
    private static final long serialVersionUID = 8016912633666133628L;
	private static final String SWAGGER_FILE_NAME = "api-docs.json";
	private static String irisUrlMapping;
	private static String apiKeyTokenName;
	
	public static final String SWAGGER_SERVLET_INIT_PARAM = "irisUrlMapping";
	public static final String SWAGGER_SERVLET_APIKEY_PARAM = "apiKeyTokenName";
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		// Get the servlet-pattern from the IRIS servlet
		irisUrlMapping = config.getInitParameter(SWAGGER_SERVLET_INIT_PARAM);
		apiKeyTokenName = config.getInitParameter(SWAGGER_SERVLET_APIKEY_PARAM);
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
		    
		    String fileName = req.getParameter("file");
		    if(StringUtils.isEmpty(fileName)){
		        fileName = SWAGGER_FILE_NAME;
		    }
		    
			// Parse the api-docs.json file to get a JsonReader object
			JsonReader jsonReader = Json.createReader(new InputStreamReader(getServletContext().getResourceAsStream("/" + fileName)));
			JsonObject jsonSwaggerObject = jsonReader.readObject();
			jsonReader.close();
			// Build a JsonReader object with the basePath and the data from api-docs.json in order to write it to the response
			JsonObjectBuilder builder = Json.createObjectBuilder();
			for (Entry<String, JsonValue> entry : jsonSwaggerObject.entrySet()) {
			    builder.add(entry.getKey(), entry.getValue());
			}
			
			builder.add("basePath", req.getContextPath() + "/" + irisUrlMapping);
			builder.add("host", req.getServerName() + ":" + req.getServerPort());	    
		    JsonObject securityDefinitions = Json.createObjectBuilder()
		            .add("api_key", Json.createObjectBuilder()
		                .add("type", "apiKey")
		                .add("name", (null != apiKeyTokenName && !StringUtils.isEmpty(apiKeyTokenName)) ? apiKeyTokenName : "api_key")
		                .add("in", "header"))
		            .build();
		    builder.add("securityDefinitions", securityDefinitions);
			
			JsonObject jsonFinalSwaggerObject = builder.build();
			JsonWriter jsonWriter = Json.createWriter(resp.getOutputStream());
			jsonWriter.writeObject(jsonFinalSwaggerObject);
			jsonWriter.close();
		} catch (IOException e) {
			throw new ServletException(e);
		}
	}

}