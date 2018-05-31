package tp.skt.simple.net.mqtt;

public interface IDoneCallback {
    void onDone(boolean result, int errcode, String errMessage);
}
