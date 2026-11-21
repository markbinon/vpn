package hev.htproxy;

/** JNI-обёртка над hev-socks5-tunnel. Имя пакета и класса зашито в нативной библиотеке, не менять. */
public class TProxyService {
    static {
        System.loadLibrary("hev-socks5-tunnel");
    }

    public static native void TProxyStartService(String configPath, int fd);

    public static native void TProxyStopService();
}
