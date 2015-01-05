package io.cloudbindle.youxia.q2seqware;

import io.cloudbindle.youxia.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.DefaultHttpRequestFactory;
import org.apache.http.impl.client.HttpClientBuilder;

public class SeqwareCGIMessageSource implements SeqwareJobMessageSource {

    private String hostName;
    
    @Override
    public String getMessage() {
        HttpClient client = HttpClientBuilder.create().build();
        HttpHost target = new HttpHost(this.getSourceHost());
        HttpResponse response = null;
        String responseString = null;
        try {
            HttpRequest request = DefaultHttpRequestFactory.INSTANCE.newHttpRequest("GET", this.getSourceHost());
            response = client.execute(target, request);
            if (response != null && response.getEntity() != null && response.getEntity().getContent() != null) {
                InputStream is = response.getEntity().getContent();
                StringWriter writer = new StringWriter();
                IOUtils.copy(is, writer);
                responseString = writer.toString();
            } else {
                Log.trace("Unable to get message from response content.");
            }
        } catch (IOException e) {
            Log.error("IOException: "+e.getMessage());
            //e.printStackTrace();
        } catch (MethodNotSupportedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return responseString;
    }

    @Override
    public String getSourceHost() {
        return hostName;
    }

    @Override
    public void setSourceHost(String host) {
        this.hostName = host;
    }

}
