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

/**
 * Reports the status of a seqware workflow to a CGI script at some URI on a host.
 * @author sshorser
 *
 */
public class SeqwareCGIStatusReporter implements SeqwareStatusReporter{

    public String uri;

    /**
     * get the URI that the status will be reported to.
     * @return
     */
    public String getURI() {
        return uri;
    }

    /**
     * Set the URI that the status will be reported to.
     * This should be the FULL URI, such as <i>http://someHost.com/a/b/c/statuslistener.cgi</i>
     * @param uri
     */
    public void setURI(String uri) {
        this.uri = uri;
    }

    /**
     * Report a seqware status to the host.
     * @param status - the status to report.
     */
    @Override
    public void reportSeqwareStatus(String status) {
        HttpClient client = HttpClientBuilder.create().build();
        HttpResponse response = null;
        String responseString = null;
        try {
            StringEntity stringEntity = new StringEntity(buildJSONMessage(status), ContentType.APPLICATION_JSON);
            HttpPost post = new HttpPost();
            URI uri = new URI(this.getURI());
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
            e.printStackTrace();
        }
    }

    private String buildJSONMessage(String status) {
        return "{\"status\": \"" + status + "\"}";
    }
}
