package online.mwang.foundtrading.utils;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.SneakyThrows;

import java.io.InputStream;

public class CommandUtils {

    //远程主机IP
    private static final String REMOTE_HOST = "10.0.4.9";
    //远程主机用户名
    private static final String USERNAME = "root";
    //远程主机密码
    private static final String PASSWORD = "Dknhfre1st";
    //SSH服务端口
    private static final int REMOTE_PORT = 22;
    //会话超时时间
    private static final int SESSION_TIMEOUT = 10000;
    //管道流超时时间(执行脚本超时时间)
    private static final int CHANNEL_TIMEOUT = 5000;

    @SneakyThrows
    public static void run(String cmd) {

        Session jschSession = null;

        try {

            JSch jsch = new JSch();
            //SSH授信客户端文件位置，一般是用户主目录下的.ssh/known_hosts
            jsch.setKnownHosts("/root/.ssh/known_hosts");
            jschSession = jsch.getSession(USERNAME, REMOTE_HOST, REMOTE_PORT);

            // 密码认证
            jschSession.setPassword(PASSWORD);

            // 建立session
            jschSession.connect(SESSION_TIMEOUT);
            //建立可执行管道
            ChannelExec channelExec = (ChannelExec) jschSession.openChannel("exec");

            // 执行脚本命令"sh /root/hello.sh zimug"
            channelExec.setCommand(cmd);

            // 获取执行脚本可能出现的错误日志
            channelExec.setErrStream(System.err);

            //脚本执行结果输出，对于程序来说是输入流
            InputStream in = channelExec.getInputStream();

            // 5 秒执行管道超时
            channelExec.connect(CHANNEL_TIMEOUT);

            // 从远程主机读取输入流，获得脚本执行结果
            byte[] tmp = new byte[1024];
            while (true) {
                while (in.available() > 0) {
                    int i = in.read(tmp, 0, 1024);
                    if (i < 0) break;
                    //执行结果打印到程序控制台
                    System.out.print(new String(tmp, 0, i));
                }
                if (channelExec.isClosed()) {
                    if (in.available() > 0) continue;
                    //获取退出状态，状态0表示脚本被正确执行
                    System.out.println("exit-status: "
                            + channelExec.getExitStatus());
                    break;
                }

            }

            channelExec.disconnect();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (jschSession != null) {
                jschSession.disconnect();
            }
        }

    }
}
