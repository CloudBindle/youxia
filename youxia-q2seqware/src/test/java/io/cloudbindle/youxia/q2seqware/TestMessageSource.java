package io.cloudbindle.youxia.q2seqware;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@PrepareForTest({ org.apache.http.impl.client.HttpClientBuilder.class, org.apache.http.client.HttpClient.class,
        org.apache.http.HttpResponse.class })
@RunWith(PowerMockRunner.class)
// @PowerMockIgnore("javax.net.ssl.*")
public class TestMessageSource {

    private static final String TEST_STRING = "test string";

    private static final Object TEST_EXCEPTION_MESSAGE = "Test Exception";

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

    }

    private void setUpMocks(boolean returnNull) throws IOException, Exception {
        // first, set up the builder to return the mock client.
        Mockito.doReturn((CloseableHttpClient) mockClient).when(builder).build();

        setUpResponse(returnNull);

        // Now do a MockStatic so that the HttpClientBuilder class will always yield the builder object we set up earlier.
        PowerMockito.mockStatic(HttpClientBuilder.class);
        PowerMockito.when(HttpClientBuilder.class, "create").thenReturn(builder);
    }

    private void setUpResponse(boolean returnNull) throws IOException {
        // now, set up the response
        if (!returnNull) {
            ByteArrayInputStream stream = new ByteArrayInputStream(TEST_STRING.getBytes());
            Mockito.when(mockEntity.getContent()).thenReturn(stream);
            Mockito.when(mockEntity.isStreaming()).thenReturn(false);
            Mockito.when(mockResponse.getEntity()).thenReturn(mockEntity);

            // set up the mock client to return the response
            Mockito.when(mockClient.execute(any(HttpHost.class), any(HttpRequest.class))).thenReturn(mockResponse);
        } else {
            Mockito.when(mockClient.execute(any(HttpHost.class), any(HttpRequest.class))).thenReturn(null);
        }
    }

    @Test
    public void testGetMessageFromURI() throws Exception {
        setUpMocks(false);
        SeqwareCGIMessageSource msgSource = new SeqwareCGIMessageSource();
        msgSource.setSourceHost("http://localhost");

        String message = msgSource.getMessage();
        System.out.println(message);
        assertNotNull(message);
        assertEquals(TEST_STRING, message);
    }

    @Test
    public void testResponseIsNull() throws IOException, Exception {
        setUpMocks(true);
        SeqwareCGIMessageSource msgSource = new SeqwareCGIMessageSource();
        msgSource.setSourceHost("http://localhost");

        String message = msgSource.getMessage();
        System.out.println(message);
        assertNull(message);
    }

    @Test
    public void testIOException() throws Exception {
        setUpMocks(false);
        try {
            Mockito.when(mockClient.execute(any(HttpHost.class), any(HttpRequest.class))).thenThrow(new IOException("Test Exception"));
            SeqwareCGIMessageSource msgSource = new SeqwareCGIMessageSource();
            msgSource.setSourceHost("http://localhost");
            String message = msgSource.getMessage();
        } catch (IOException e) {
            assertTrue(e.getMessage().equals(TEST_EXCEPTION_MESSAGE));
        }
    }
}
