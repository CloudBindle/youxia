package io.cloudbindle.youxia.q2seqware;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;

import java.io.ByteArrayInputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.google.gson.JsonElement;
import com.google.gson.JsonStreamParser;

@PrepareForTest({ org.apache.http.impl.client.HttpClientBuilder.class, org.apache.http.client.HttpClient.class,
        org.apache.http.HttpResponse.class,org.apache.http.client.methods.CloseableHttpResponse.class })
@RunWith(PowerMockRunner.class)
public class TestMessageStatusReporter {

    private static final String TEST_STRING = "200;OK";

    @Mock
    CloseableHttpClient mockClient;

    @Mock
    CloseableHttpResponse mockResponse;
    
    @Mock
    StringEntity mockEntity;
    
    @Spy
    HttpClientBuilder builder = HttpClientBuilder.create();

    @Before
    public void init() throws Exception {
        MockitoAnnotations.initMocks(this);

        ByteArrayInputStream stream = new ByteArrayInputStream(TEST_STRING.getBytes());
        Mockito.when(mockResponse.getEntity()).thenReturn(mockEntity );
        Mockito.when(mockEntity.getContent()).thenReturn(stream);
        Mockito.when(mockEntity.isStreaming()).thenReturn(false);
        
        // first, set up the builder to return the mock client.
        Mockito.doReturn(mockClient).when(builder).build();

        //TODO: set some mock response(s) based on what we want to do for status reporting response:
        // OK - Adam's system has received our response.
        // Other/Err - status report was NOT received. Try again after some interval? Or just
        // ignore the error and let the normal report-after-interval process continue. Probably
        // best to log the error, either way.
        
        // Now, set up the client so that it will capture anything that is passed when execute is called.
        PowerMockito.when(mockClient.execute(any(HttpPost.class))).then(new Answer<HttpResponse>() {
            @Override
            public HttpResponse answer(InvocationOnMock invocation) throws Throwable {
                HttpEntity entity = ((HttpPost)invocation.getArguments()[0]).getEntity();
                String entityString = EntityUtils.toString(entity);
                System.out.println(entityString);
                //We should check that the message that was sent is valid JSON.
                JsonStreamParser parser = new JsonStreamParser(entityString);
                assertTrue(parser.hasNext());
                JsonElement element = parser.next();
                assertTrue(element.isJsonObject());
                Mockito.when(mockResponse.getEntity()).thenReturn(mockEntity);
                return mockResponse;
            }
        });
        // Now do a MockStatic so that the HttpClientBuilder class will always yield the builder object we set up earlier.
        PowerMockito.mockStatic(HttpClientBuilder.class);
        PowerMockito.when(HttpClientBuilder.class, "create").thenReturn(builder);

    }

    @Test
    public void testReportStatus() {
        SeqwareStatusReporter reporter = new SeqwareStatusReporter();
        reporter.setHost("http://localhost/");
        reporter.reportSeqwareStatus("running");
    }
}
