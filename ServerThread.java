import java.io.*;
import java.net.*;
import java.util.*;

public class ServerThread implements Runnable{

    // 用一个Map存储客户端的用户名和Socket
    public static Map<String, Socket> clients = new HashMap<>();

    // 用一个Map存储客户端的用户名和IP地址以及UDP端口号
    public static Map<String, Users> clientsInfo = new HashMap<>();

    // 用一个Set存储在线的用户名
    public static Set<String> onlineUsers = new HashSet<>();
    private Socket clientSocket;

    public static DatagramSocket datagramSocket;

    private BufferedReader br;
    private PrintWriter pw;

    // 服务器UDP接收端口号
    private static final int SERVER_UDP_PORT = 9999;

    static {
        try {
            datagramSocket = new DatagramSocket(SERVER_UDP_PORT);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

    class Users{
        private String name;//用户名
        private String IP;//对应IP地址
        private int Client_UDP_PORT;//对应UDP接收端口号
        public Users(String name,String IP,int Client_UDP_PORT){
            this.name=name;
            this.IP=IP;
            this.Client_UDP_PORT=Client_UDP_PORT;
        }

        public String getIP() {
            return IP;
        }

        public int getClient_UDP_PORT() {
            return Client_UDP_PORT;
        }
    }

    public ServerThread(Socket clientSocket){
        this.clientSocket=clientSocket;
        try {
            // 获取输入输出流
            br = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            pw = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println(e);
        }
    }

    public synchronized void run(){
        try{
            while (true) {
                // 解析客户端发送的消息，格式为：type:content
                String message=br.readLine();
                String[] parts = message.split(":");
                String type = parts[0]; // 消息类型
                String sender = parts[1];//消息发送者
                String content = parts[2]; // 消息内容

                switch (type) {
                    case "login": // 登录消息
                        login(sender,content);
                        break;
                    case "logout": // 登出消息
                        logout(sender);
                        break;
                    case "chat":
                        chatWith(content);
                        break;
                    case "broadcast": // 广播消息
                        content = type + ":" + "用户" + sender + "：" + content;
                        broadcast(content);
                        break;
                    default:
                        System.out.println("无效的消息类型：" + type);
                }
            }
        }catch(IOException e){
            e.printStackTrace();
            System.out.println("服务器处理消息出现异常！");
        }finally {
            try {
                // 关闭资源
                br.close();
                pw.close();
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println(e);
            }
        }
    }

    // 处理登录消息
    public void login(String sender,String UDP_PORT) {
        // 将用户名和Socket添加到Map中
        clients.put(sender, clientSocket);
        // 将用户名添加到在线用户集合中
        onlineUsers.add(sender);

        // 将用户名和对应用户信息添加到Map中
        int UDP=Integer.parseInt(UDP_PORT);
        Users user=new Users(sender,clientSocket.getInetAddress().getHostAddress(),UDP);
        clientsInfo.put(sender,user);

        System.out.println("用户"+sender + "登录成功");
        // 向客户端发送登录成功的消息，格式为：login:ok
        pw.println("login:ok");
        pw.flush();
        // 向所有客户端发送在线用户列表更新的消息，格式为：users:user1,user2,...
        broadcast("users:" + String.join(",", onlineUsers));
    }

    // 处理登出消息
    public void logout(String sender) {
        // 将用户名和Socket从Map中移除
        clients.remove(sender);

        // 将用户名从在线用户集合中移除
        onlineUsers.remove(sender);

        // 将用户名和对应信息从Map中移除
        clientsInfo.remove(sender);
        System.out.println(sender + "登出成功");
        // 向客户端发送登出成功的消息，格式为：logout:ok
        pw.println("logout:ok");
        pw.flush();
        // 向所有客户端发送在线用户列表更新的消息，格式为：users:user1,user2,...
        broadcast("users:" + String.join(",", onlineUsers));
    }

    public void chatWith(String receiver) {
        String IP=clientsInfo.get(receiver).getIP();
        int UDP=clientsInfo.get(receiver).getClient_UDP_PORT();

        try{
            PrintWriter targetPw = new PrintWriter(new OutputStreamWriter(clients.get(receiver).getOutputStream()));
            targetPw.println("chatWith:"+receiver);
            targetPw.flush();
        }catch(IOException e){
            e.printStackTrace();
            System.out.println(e);
        }

        try{
            byte[] receiveData = new byte[1024]; // 创建一个字节数组，用于存储接收到的数据
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length); // 创建一个数据报对象
            while(true){
                datagramSocket.receive(receivePacket);
                DatagramSocket sendSocket = new DatagramSocket();
                InetAddress address = InetAddress.getByName(IP); // 获取接收方地址
                int port = UDP; // 获取接收者的UDP端口号
                DatagramPacket sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getData().length, address, port); // 创建一个数据报对象
                sendSocket.send(sendPacket); // 发送数据报

                String message = new String(receivePacket.getData(), 0, receivePacket.getLength()); // 将数据报转换为字符串
                String[] parts = message.split(":");
                String content = parts[1]; // 消息内容

                if(content.equals("bye")){
                    break;
                }
            }

        }catch(IOException e){
            e.printStackTrace();
            System.out.println(e);
        }
    }

    // 向所有客户端广播消息
    public void broadcast(String content) {
        for (Socket socket : clients.values()) {
            try {
                PrintWriter targetPw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
                targetPw.println(content);
                targetPw.flush();
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println(e);
            }
        }
    }
}
