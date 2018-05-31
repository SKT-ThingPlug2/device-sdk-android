package tp.skt.simple.net.mqtt;

import android.content.Context;
import android.text.TextUtils;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import tp.skt.simple.common.Define;
import tp.skt.simple.common.Util;

/**
 * MQTT client
 * <p>
 * Copyright (C) 2017. SK Telecom, All Rights Reserved.
 * Written 2017, by SK Telecom
 */
public class MQTTClient {
    /**
     * Context
     **/
    private Context context;
    private String baseUrl;
    private String clientID;
    private String userName;
    private String password;
    private String version;
    private boolean enableSecure;
    private boolean cleanSession;

    /**
     * MqttAndroidClient
     **/
    private MqttAndroidClient mqttAndroidClient;
    /**
     * Subscribe Topics
     **/
    private String[] subscribeTopics;
    /**
     * SimpleListener
     **/
    private SimpleListener simpleListener;

    /**
     * MQTTClient constructor
     *
     * @param context
     * @param baseUrl
     * @param clientID
     * @param userName
     * @param password
     * @param version  if value is null, default value is 1.0
     */
    MQTTClient(Context context, String baseUrl, String clientID, String userName, String password, String version, String[] subscribeTopics, boolean enableSecure, boolean cleanSession) {
        this.context = context.getApplicationContext();
        this.baseUrl = baseUrl;
        this.clientID = clientID;
        this.userName = userName;
        this.password = password;
        this.version = (version == null ? Define.VERSION : version);
        this.subscribeTopics = subscribeTopics;
        this.enableSecure = enableSecure;
        this.cleanSession = cleanSession;
    }

    /**
     * disconnect
     */
    public void disconnect() {
        if (mqttAndroidClient != null && mqttAndroidClient.isConnected()) {

            unsubscribeTopic(new IDoneCallback() {
                @Override
                public void onDone(boolean result, int errCode, String errMessage) {
                    try {
                        mqttAndroidClient.disconnect(null, null);
                    } catch (MqttException e) {
                        e.printStackTrace();
                    }
                }
            });

        }
    }

    /**
     *
     *
     */
    public void destroy() {
        if (mqttAndroidClient != null) {
            mqttAndroidClient.unregisterResources();
            mqttAndroidClient.close();
            Util.log("destroy");
        }
    }

    /**
     * connect server
     *
     * @return
     */
    public void connect(final SimpleListener simpleListener) {
        this.simpleListener = simpleListener;
        // MQTT Client 생성및 설정.
        this.mqttAndroidClient = new MqttAndroidClient(context, baseUrl, clientID);
        this.mqttAndroidClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                if (reconnect == true) {
                    Util.log("Reconnected to : " + serverURI);
                    // Because Clean Session is true, we need to re-subscribe
                    if(cleanSession){
                        subscribeTopic();
                    }
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                if (cause != null) {
                    Util.log(cause.getMessage());
                }
                if (simpleListener != null) {
                    simpleListener.onConnectionLost();
                }
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                if (message.isDuplicate() == true) {
                    Util.log("message duplicated!");
                    return;
                }
                String receivedMessage = message.toString();
                Util.log("messageArrived : " + topic + " - " + receivedMessage + this.toString());
                simpleListener.onPush(receivedMessage);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                Util.log("message delivered");
                simpleListener.onDelivered();
            }
        });






        try {


            final MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
            mqttConnectOptions.setCleanSession(false);
            mqttConnectOptions.setAutomaticReconnect(true);

            if(false == enableSecure){
                TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                        // Not implemented
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                        // Not implemented
                    }
                } };

                SSLContext sc = SSLContext.getInstance("TLS");
                sc.init(null, trustAllCerts, new java.security.SecureRandom());
                mqttConnectOptions.setSocketFactory(sc.getSocketFactory());
            }

            if (this.userName != null) {
                Util.log("userName : " + this.userName);
                mqttConnectOptions.setUserName(this.userName);
            }
            if (this.password != null) {
                Util.log("password : " + this.password);
                mqttConnectOptions.setPassword(this.password.toCharArray());
            }

            mqttAndroidClient.connect(mqttConnectOptions, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    simpleListener.onConnected();
                    subscribeTopic();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    if (exception != null) {
                        exception.printStackTrace();
                    }
                    simpleListener.onConnectFailure();
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
            simpleListener.onConnectFailure();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
    }

    /**
     * subscribe topic
     */
    private void subscribeTopic() {
        if (mqttAndroidClient == null || mqttAndroidClient.isConnected() == false || subscribeTopics == null) {
            simpleListener.onSubscribeFailure();
            return;
        }
        try {
            int[] qosArray = new int[subscribeTopics.length];
            Arrays.fill(qosArray, 1);

            mqttAndroidClient.subscribe(subscribeTopics, qosArray, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Util.log(Arrays.toString(asyncActionToken.getTopics()));
                    simpleListener.onSubscribed();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Util.log(Arrays.toString(asyncActionToken.getTopics()));
                    simpleListener.onSubscribeFailure();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            simpleListener.onSubscribeFailure();
        }
    }

    private void unsubscribeTopic(final IDoneCallback callback) {
        try {
            mqttAndroidClient.unsubscribe(subscribeTopics, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    callback.onDone(true, 0, null);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    callback.onDone(false, -1, exception.getMessage());
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    /**
     * publish message
     *
     * @param topic
     * @param payload
     * @param callBack
     * @throws MqttException
     */
    public void publish(String topic, String payload, final SimpleCallback callBack) throws MqttException {
        MqttMessage message = new MqttMessage();
        message.setPayload(payload.getBytes());
        Util.log("publishTopic : " + topic);
//        Util.log("publishMessage : " + payload);
        while (payload.length() > 0) {
            if (payload.length() > 4000) {
                Util.log(payload.substring(0, 4000));
                payload = payload.substring(4000);
            } else {
                Util.log(payload);
                break;
            }
        }

        mqttAndroidClient.publish(topic, message, context, new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {
                Util.log("message publish success");
                callBack.onResponse(asyncActionToken.getMessageId());
            }

            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                String message = "";
                if (exception != null) {
                    message = exception.getMessage();
                    Util.log(message);
                }
                callBack.onFailure(asyncActionToken.getMessageId(), message);
            }
        });
    }

    /**
     * MQTT is connected
     *
     * @return true : connected <-> false
     */
    public boolean isMQTTConnected() {
        if (mqttAndroidClient == null) {
            return false;
        }
        return mqttAndroidClient.isConnected();
    }

    /**
     * MQTTClient builder
     */
    public static class Builder {
        private String baseUrl;
        private String clientId;
        private String userName;
        private String password;
        private String version;
        private String[] subscribeTopics;
        private boolean enableSecure = true;
        private boolean cleanSession = true;
        private Context context;


        /**
         * @param context
         */
        public Builder(Context context) {
            this.context = context.getApplicationContext();
        }

        /**
         * @param baseUrl
         * @return
         */
        public Builder baseUrl(String baseUrl) {
            Util.checkNull(baseUrl, "baseUrl = null");
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * @param clientId
         * @return
         */
        public Builder clientId(String clientId) {
            Util.checkNull(clientId, "clientID = null");
            this.clientId = clientId;
            return this;
        }

        /**
         * @param userName
         * @return
         */
        public Builder userName(String userName) {
            Util.checkNull(userName, "userName = null");
            this.userName = userName;
            return this;
        }

        /**
         * @param password
         * @return
         */
        public Builder password(String password) {
//            Util.checkNull(password, "password = null");
            this.password = password;
            return this;
        }

        /**
         * @param enabled
         * @return
         */
        public Builder setLog(boolean enabled) {
            Util.setLogEnabled(enabled);
            return this;
        }

        /**
         * set API version info
         *
         * @param version
         * @return Builder
         */
        public Builder setVersion(String version) {
            if (TextUtils.isEmpty(version) == false) {
                this.version = version;
            } else {
                throw new IllegalArgumentException("Invalid version value!");
            }
            return this;
        }

        /**
         * set subscribe topics
         *
         * @param subscribeTopics
         * @return Builder
         */
        public Builder setSubscribeTopics(String[] subscribeTopics) {
            if (subscribeTopics != null) {
                this.subscribeTopics = subscribeTopics;
            }
            return this;
        }

        /**
         * set enable secure
         *
         * @param enableSecure
         * @return Builder
         */
        public Builder setEnableSecure(boolean enableSecure) {
            this.enableSecure = enableSecure;
            return this;
        }

        /**
         * set clean session
         *
         * @param cleanSession
         * @return Builder
         */
        public Builder setCleanSession(boolean cleanSession) {
            this.cleanSession = cleanSession;
            return this;
        }

        /**
         * @return
         */
        public MQTTClient build() {
            Util.checkNull(baseUrl, "baseUrl = null");
            return new MQTTClient(context, baseUrl, clientId, userName, password, version, subscribeTopics, enableSecure, cleanSession);
        }
    }
}
