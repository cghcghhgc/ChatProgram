import java.io.*;
import java.net.*;

public class Server {

    // 服务器TCP端口号
    public static final int PORT = 8888;

    public static ServerSocket serverSocket;

    static {
        try {
            serverSocket = new ServerSocket(PORT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try{
            System.out.println("服务器监听在"+PORT+"端口号，等待客户端连接...");
            while(true){
                Socket socket=serverSocket.accept();
                System.out.println("客户端:"+socket.getInetAddress().getHostAddress()+"连接成功");
                ServerThread serverThread=new ServerThread(socket);
                new Thread(serverThread).start();
            }
        }catch (IOException e){
            e.printStackTrace();
            System.out.println(e);
            System.out.println("服务器端终止运行");
        }
    }
}
