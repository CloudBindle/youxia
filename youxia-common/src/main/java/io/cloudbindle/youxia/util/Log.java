package io.cloudbindle.youxia.util;

import com.google.gson.Gson;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * Log class.
 * </p>
 * 
 * @author dyuen
 * @version $Id: $Id
 */
public class Log {

    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

    public static class SlackPayload {
        private String text;
        private String username;

        public SlackPayload(String username, String text) {
            this.text = text;
            this.username = username;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        /**
         * @return the username
         */
        public String getUsername() {
            return username;
        }

        /**
         * @param username
         *            the username to set
         */
        public void setUsername(String username) {
            this.username = username;
        }

    }

    private static void sendToSlack(String classname, String message) {
        HierarchicalINIConfiguration youxiaConfig = ConfigTools.getYouxiaConfig();
        if (youxiaConfig.containsKey(Constants.SLACK_URL)) {
            try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
                HttpPost httpPost = new HttpPost(youxiaConfig.getString(Constants.SLACK_URL));
                Gson gson = new Gson();
                String toJson = gson.toJson(new SlackPayload(StringUtils.substringAfter(classname, "."), message));
                httpPost.setEntity(new StringEntity(toJson));
                CloseableHttpResponse response2 = httpclient.execute(httpPost);
                response2.close();
            } catch (IOException e) {
                Log.error("Could not write to slack URL");
            }
        }
    }

    /**
     * See {@link org.apache.log4j.Logger#debug(Object)}.
     * 
     * @param message
     *            the message to log.
     */
    public static void trace(final String message) {
        Logger logger = LoggerFactory.getLogger(getCallerClassName());
        logger.trace(message);
    }

    /**
     * See {@link org.apache.log4j.Logger#debug(Object,Throwable)}.
     * 
     * @param message
     *            the message to log.
     * @param t
     *            the error stack trace.
     */
    public static void trace(final String message, final Throwable t) {
        Logger logger = LoggerFactory.getLogger(getCallerClassName());
        logger.trace(message, t);
    }

    /**
     * See {@link org.apache.log4j.Logger#debug(Object)}.
     * 
     * @param message
     *            the message to log.
     */
    public static void debug(final String message) {
        Logger logger = LoggerFactory.getLogger(getCallerClassName());
        logger.debug(message);
    }

    /**
     * See {@link org.apache.log4j.Logger#debug(Object,Throwable)}.
     * 
     * @param message
     *            the message to log.
     * @param t
     *            the error stack trace.
     */
    public static void debug(final String message, final Throwable t) {
        Logger logger = LoggerFactory.getLogger(getCallerClassName());
        logger.debug(message, t);
    }

    /**
     * See {@link org.apache.log4j.Logger#info(Object)}.
     * 
     * @param message
     *            the message to log.
     */
    public static void info(final String message) {
        Logger logger = LoggerFactory.getLogger(getCallerClassName());
        logger.info(message);
    }

    /**
     * See {@link org.apache.log4j.Logger#info(Object,Throwable)}.
     * 
     * @param message
     *            the message to log.
     * @param t
     *            the error stack trace.
     */
    public static void info(final String message, final Throwable t) {
        Logger logger = LoggerFactory.getLogger(getCallerClassName());
        logger.info(message, t);
    }

    /**
     * See {@link org.apache.log4j.Logger#warn(Object)}.
     * 
     * @param message
     *            the message to log.
     */
    public static void warn(final String message) {
        Logger logger = LoggerFactory.getLogger(getCallerClassName());
        logger.warn(message);
    }

    /**
     * See {@link org.apache.log4j.Logger#warn(Object,Throwable)}.
     * 
     * @param message
     *            the message to log.
     * @param t
     *            the error stack trace.
     */
    public static void warn(final String message, final Throwable t) {
        Logger logger = LoggerFactory.getLogger(getCallerClassName());
        logger.warn(message, t);
    }

    /**
     * See {@link org.apache.log4j.Logger#error(Object)}.
     * 
     * @param message
     *            the message to log.
     */
    public static void error(final String message) {
        Logger logger = LoggerFactory.getLogger(getCallerClassName());
        logger.error(message);
    }

    /**
     * See {@link org.apache.log4j.Logger#error(Object,Throwable)}.
     * 
     * @param message
     *            the message to log.
     * @param t
     *            the error stack trace.
     */
    public static void error(final String message, final Throwable t) {
        Logger logger = LoggerFactory.getLogger(getCallerClassName());
        logger.error(message, t);
    }

    /**
     * <p>
     * stdout.
     * </p>
     * 
     * @param message
     *            a {@link java.lang.String} object.
     */
    public static void stdout(final String message) {
        Log.stdoutWithTime(message);
        Log.sendToSlack(getCallerClassName(), message);
    }

    /**
     * Output to stdout with the time pre-pended
     * 
     * @param message
     *            a {@link java.lang.String} object.
     */
    public static void stdoutWithTime(final String message) {
        // get current date time with Date()
        Date date = new Date();
        System.out.print("[" + DATE_FORMAT.format(date) + "] | ");
        System.out.println(message);
        Log.sendToSlack(getCallerClassName(), message);
    }

    /**
     * Output to stdout with the time pre-pended
     * 
     * @param message
     *            a {@link java.lang.String} object.
     */
    public static void stderrWithTime(final String message) {
        // get current date time with Date()
        Date date = new Date();
        System.err.print("[" + DATE_FORMAT.format(date) + "] | ");
        System.err.println(message);
        Log.sendToSlack(getCallerClassName(), message);
    }

    /**
     * <p>
     * stderr.
     * </p>
     * 
     * @param message
     *            a {@link java.lang.String} object.
     */
    public static void stderr(final String message) {
        Log.stderrWithTime(message);
        Log.sendToSlack(getCallerClassName(), message);
    }

    // Private means that this class is a static singleton.
    private Log() {
    }

    /**
     * Info about the logger caller
     */
    private static class CallInfo {

        public String className;
        public String methodName;

        public CallInfo() {
        }

        public CallInfo(String className, String methodName) {
            this.className = className;
            this.methodName = methodName;
        }
    }

    /**
     * @return the className of the class actually logging the message
     */
    private static String getCallerClassName() {
        final int level = 5;
        return getCallerClassName(level);
    }

    /**
     * @return the className of the class actually logging the message
     */
    private static String getCallerClassName(final int level) {
        CallInfo ci = getCallerInformations(level);
        return ci.className;
    }

    /**
     * Examine stack trace to get caller
     * 
     * @param level
     *            method stack depth
     * @return who called the logger
     */
    private static CallInfo getCallerInformations(int level) {
        StackTraceElement[] callStack = Thread.currentThread().getStackTrace();
        StackTraceElement caller = callStack[level];
        return new CallInfo(caller.getClassName(), caller.getMethodName());
    }

}
