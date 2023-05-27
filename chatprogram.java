
//客户端代码
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;

public class Client extends JFrame {
    // 用一个Map存储私聊窗口对象，键为用户名，值为ChatWindow对象
    private Map<String, ChatWindow> chatWindows = new HashMap<>();

    // 客户端用户名和密码
    private String username;

    // 客户端Socket
    public static Socket socket;

    // 输入输出流
    private BufferedReader br;
    private PrintWriter pw;

    // 界面组件
    private JTextField usernameField; // 用户名输入框
    private JButton loginButton; // 登录按钮
    private JButton logoutButton; // 登出按钮
    private JList<String> userList; // 在线用户列表
    private JTextArea chatArea; // 聊天区域
    private JTextField inputField; // 输入框
    private JButton sendButton; // 发送按钮

    // 服务器地址和端口号
    private static final String HOST = "10.133.2.38";
    private static final int PORT = 8888;

    // 本地UDP接收端口号
    private static final int UDP_PORT = 9576;

    // 服务器UDP接收端口号
    private static final int SERVER_UDP_PORT = 9999;

    public Client() {
        // 设置窗口标题和大小
        super("局域网聊天程序");

        // 设置窗口居中显示和关闭动作
        setLocationRelativeTo(null);

        // 设置窗口布局为边界布局
        setLayout(new BorderLayout());

        // 创建一个面板，放置用户名输入框和登录登出按钮，添加到窗口北部
        JPanel northPanel = new JPanel();
        northPanel.setLayout(new FlowLayout());
        usernameField = new JTextField(10);
        loginButton = new JButton("登录");
        logoutButton = new JButton("登出");
        northPanel.add(new JLabel("用户名："));
        northPanel.add(usernameField);
        northPanel.add(loginButton);
        northPanel.add(logoutButton);
        add(northPanel, BorderLayout.NORTH);

        // 创建一个面板，放置在线用户列表，添加到窗口西部
        JPanel westPanel = new JPanel();
        westPanel.setLayout(new BorderLayout());
        userList = new JList<>();
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); // 设置列表只能单选
        westPanel.add(new JLabel("在线用户"), BorderLayout.NORTH);
        westPanel.add(new JScrollPane(userList), BorderLayout.CENTER);
        add(westPanel, BorderLayout.WEST);

        // 创建一个面板，放置聊天区域、输入框和发送按钮，添加到窗口中部
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BorderLayout());
        chatArea = new JTextArea();
        chatArea.setEditable(false); // 设置聊天区域不可编辑
        inputField = new JTextField();
        sendButton = new JButton("发送");
        centerPanel.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        centerPanel.add(inputField, BorderLayout.SOUTH);
        centerPanel.add(sendButton, BorderLayout.EAST);
        add(centerPanel, BorderLayout.CENTER);

        logoutButton.setEnabled(false);

        // 添加登录按钮的点击事件监听器
        loginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                login();
            }
        });

        // 添加登出按钮的点击事件监听器
        logoutButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                logout();
            }
        });

        // 添加发送按钮的点击事件监听器
        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                send();
            }
        });

        // 添加用户列表的双击事件监听器
        userList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) { // 双击
                    openChatWindow(); // 打开聊天窗口
                }
            }
        });

        // 连接服务器
        connect();
    }

    // 连接服务器
    public void connect() {
        try {
            socket = new Socket(HOST, PORT);
            System.out.println("连接服务器成功");
            // 获取输入输出流
            br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));

            // 创建一个新的线程，用于接收服务器的消息
            new Thread(new ServerHandler()).start();
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println(e);
        }
    }

    // 登录服务器
    public void login() {
        // 获取用户名输入框的内容，去掉首尾空格
        username = usernameField.getText().trim();
        if (username.isEmpty()) { // 如果用户名为空，弹出提示框
            JOptionPane.showMessageDialog(this, "用户名不能为空");
            return;
        }
        // 向服务器发送登录消息，格式为：login:username:udpPort
        pw.println("login:" + username + ":" + UDP_PORT);
        pw.flush();

    }

    // 登出服务器
    public void logout() {
        // 向服务器发送登出消息，格式为：logout:username
        pw.println("logout:" + username + ":" + UDP_PORT);
        pw.flush();

    }

    public void send() {
        // 获取输入框的内容，去掉首尾空格
        String message = inputField.getText().trim();
        if (message.isEmpty()) {
            JOptionPane.showMessageDialog(this, "消息不能为空");
            return;
        }
        pw.println("broadcast:" + username + ":" + message);
        pw.flush();
        inputField.setText("");
    }

    // 打开聊天窗口
    public void openChatWindow() {
        // 获取用户列表的选中项
        String receiver = userList.getSelectedValue();

        // 如果选中的是自己，弹出提示框
        if (receiver.equals(username)) {
            JOptionPane.showMessageDialog(this, "不能给自己发消息");
            return;
        }
        // 创建一个新的聊天窗口对象，并将其添加到一个Map中，以便后续使用
        ChatWindow chatWindow = new ChatWindow(receiver);
        chatWindows.put(receiver, chatWindow);
    }

    // 定义一个内部类，实现Runnable接口，用于接收服务器的消息
    class ServerHandler implements Runnable {
        @Override
        public synchronized void run() {
            try {

                while (true) {
                    // 解析服务器发送的消息，格式为：type:content
                    String message=br.readLine();
                    String[] parts = message.split(":");
                    String type = parts[0]; // 消息类型
                    String content = parts[1]; // 消息内容
                    System.out.println("\ntype: " + type);

                    switch (type) {
                        case "login": // 登录消息
                            handleLogin(content);
                            break;
                        case "logout": // 登出消息
                            handleLogout(content);
                            break;
                        case "users": // 在线用户列表消息
                            handleUsers(content);
                            break;
                        case "chatWith":
                            receiveHandler();
                            break;
                        case "broadcast": // 广播消息
                            handleBroadCast(content);
                            break;
                        default:
                            System.out.println("无效的消息类型：" + type);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println(e);
            } finally {
                try {
                    // 关闭资源
                    br.close();
                    pw.close();
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    System.out.println(e);
                }
            }
        }

        // 处理登录消息
        public void handleLogin(String content) {
            if (content.equals("ok")) { // 如果登录成功，弹出提示框，并设置用户名输入框和登录按钮不可用，登出按钮可用
                JOptionPane.showMessageDialog(Client.this, "登录成功");
                usernameField.setEnabled(false);
                loginButton.setEnabled(false);
                logoutButton.setEnabled(true);
            } else { // 如果登录失败，弹出提示框，并清空用户名输入框
                JOptionPane.showMessageDialog(Client.this, "登录失败");
                usernameField.setText("");
            }
        }

        // 处理登出消息
        public void handleLogout(String content) {
            if (content.equals("ok")) { // 如果登出成功，弹出提示框，并设置用户名输入框和登录按钮可用，登出按钮不可用，清空在线用户列表和聊天区域
                JOptionPane.showMessageDialog(Client.this, "登出成功");
                usernameField.setEnabled(true);
                loginButton.setEnabled(true);
                logoutButton.setEnabled(false);
                userList.setListData(new String[0]);
                chatArea.setText("");
            } else { // 如果登出失败，弹出提示框
                JOptionPane.showMessageDialog(Client.this, "登出失败");
            }
        }

        // 处理在线用户列表消息
        public void handleUsers(String content) {
            // 解析在线用户列表，格式为：user1,user2,...
            String[] users = content.split(",");
            // 将在线用户列表显示在用户列表组件中
            userList.setListData(users);
        }

        // 处理广播消息
        public void handleBroadCast(String content) {
            chatArea.append(content + "\n");
        }
    }

    public synchronized void receiveHandler() {
        try {
            byte[] receiveData = new byte[1024]; // 创建一个字节数组，用于存储接收到的数据
            DatagramSocket datagramSocket = new DatagramSocket(UDP_PORT);
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length); // 创建一个数据报对象
            while (true) {
                datagramSocket.receive(receivePacket);
                String message = new String(receivePacket.getData(), 0, receivePacket.getLength()); // 将数据报转换为字符串
                // 解析消息，格式为：sender:message
                String[] parts = message.split(":");
                String sender = parts[0];//消息发送者
                String content = parts[1]; // 消息内容

                if (sender.equals(username)) {
                    continue;
                } else {
                    ChatWindow chatWindow = chatWindows.get(sender); // 获取发送者的聊天窗口对象
                    if (chatWindow == null) { // 如果没有打开过聊天窗口，创建一个新的对象，并添加到Map中
                        chatWindow = new ChatWindow(sender);
                        chatWindows.put(sender, chatWindow);
                    }

                    chatWindow.append(sender + "说：" + content + "\n");
                }

                if(content.equals("bye")){
                    datagramSocket.close();
                    break;
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println(e);
        }
    }

    // 定义一个内部类，继承JFrame类，用于创建私聊窗口
    class ChatWindow extends JFrame {
        private JTextArea chatArea; // 聊天区域
        private JTextField inputField; // 输入框
        private JButton sendButton; // 发送按钮

        public ChatWindow(String receiver) {
            // 设置窗口标题和大小
            setTitle("与" + receiver + "的私聊");
            setSize(400, 300);

            // 设置窗口居中显示和关闭动作
            setLocationRelativeTo(null);
            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

            // 设置窗口布局为边界布局
            setLayout(new BorderLayout());

            // 创建一个面板，放置聊天区域、输入框和发送按钮，添加到窗口中部
            JPanel centerPanel = new JPanel();
            centerPanel.setLayout(new BorderLayout());
            chatArea = new JTextArea();
            chatArea.setEditable(false); // 设置聊天区域不可编辑
            inputField = new JTextField();
            sendButton = new JButton("发送");
            centerPanel.add(new JScrollPane(chatArea), BorderLayout.CENTER);
            centerPanel.add(inputField, BorderLayout.SOUTH);
            centerPanel.add(sendButton, BorderLayout.EAST);
            add(centerPanel, BorderLayout.CENTER);

            // 设置窗口可见
            setVisible(true);

            // 添加发送按钮的点击事件监听器
            sendButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    send(receiver); // 调用发送方法，传入接收者的用户名
                }
            });
        }

        // 发送消息
        public synchronized void send(String receiver){
            // 获取输入框的内容，去掉首尾空格
            String message = inputField.getText().trim();

            if (message.isEmpty()) { // 如果消息为空，弹出提示框
                JOptionPane.showMessageDialog(this, "消息不能为空");
                return;
            }

            inputField.setText(""); // 清空输入框

            pw.println("chat:"+username+":"+receiver);//告诉服务器通知接收方准备接收数据包
            pw.flush();

            try {
                DatagramSocket sendSocket = new DatagramSocket();
                byte[] data = (username + ":" + message).getBytes(); // 将消息转换为字节数组
                InetAddress address = InetAddress.getByName(HOST); // 获取接收方地址
                int port = SERVER_UDP_PORT; // 获取接收者的UDP端口号
                DatagramPacket packet = new DatagramPacket(data, data.length, address, port); // 创建一个数据报对象
                sendSocket.send(packet); // 发送数据报
                chatArea.append("你对" + receiver + "说：" + message + "\n"); // 在聊天区域显示消息
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println(e);
            }
        }

        // 在聊天区域追加消息
        public void append(String message) {
            SwingUtilities.invokeLater(new Runnable(){
                @Override
                public void run() {
                    chatArea.append(message); // 在聊天区域追加消息
                    chatArea.setCaretPosition(chatArea.getText().length()); // 将光标移动到最后一行
                }
            });

        }
    }

    public static void main(String[] args) {
        Client client=new Client();
        client.setSize(600, 400);
        client.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        client.setVisible(true);
    }
}


//服务端代码
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

