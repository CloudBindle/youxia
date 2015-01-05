package io.cloudbindle.youxia.q2seqware;

import io.cloudbindle.youxia.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;

public class SeqwareStatusReporter {

    public String host;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void reportSeqwareStatus(String status) {
        HttpClient client = HttpClientBuilder.create().build();
        HttpResponse response = null;
        String responseString = null;
        try {
            StringEntity stringEntity = new StringEntity(buildJSONMessage(status), ContentType.APPLICATION_JSON);
            HttpPost post = new HttpPost();
            URI uri = new URI(this.getHost());
            post.setURI(uri);
            post.setEntity(stringEntity);

            response = client.execute(post);
            // Do we even care about the response? We're sending a status update.
            // If the POST fails, all we can do is log it, or try again...
            if (response != null && response.getEntity() != null && response.getEntity().getContent() != null) {
                InputStream is = response.getEntity().getContent();
                StringWriter writer = new StringWriter();
                IOUtils.copy(is, writer);
                responseString = writer.toString();
                Log.trace("responseString:" + responseString);
                /*
                 * if response != Ok, log it and maybe wait and try again?
                 */
            } else {
                Log.trace("Unable to determine response from status report.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private String buildJSONMessage(String status) {
        // TODO Auto-generated method stub
        return "{\"status\": \"" + status + "\"}";
    }
}
