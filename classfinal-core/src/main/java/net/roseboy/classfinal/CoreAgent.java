package net.roseboy.classfinal;

import net.roseboy.classfinal.util.*;

import java.io.Console;
import java.io.File;
import java.lang.instrument.Instrumentation;

/**
 * 监听类加载
 *
 * @author roseboy
 */
public class CoreAgent {
    /**
     * man方法执行前调用
     *
     * @param args 参数
     * @param inst inst
     */
    public static void premain(String args, Instrumentation inst) {
        Const.pringInfo();
        CmdLineOption options = new CmdLineOption();
        options.addOption("pwd", true, "密码");
        options.addOption("debug", false, "调试模式");
        options.addOption("del", true, "读取密码后删除密码");
        options.addOption("nopwd", false, "无密码启动");

        char[] pwd;

        //读取jar隐藏的密码，无密码启动模式(jar)
        pwd = readJarPassword();

        if (args != null) {
            options.parse(args.split(" "));
            Const.DEBUG = options.hasOption("debug");
        }

        //参数标识 无密码启动
        if (options.hasOption("nopwd")) {
            pwd = new char[1];
            pwd[0] = '#';
        }

        //参数获取密码
        if (StrUtils.isEmpty(pwd)) {
            pwd = options.getOptionValue("pwd", "").toCharArray();
        }

        // 参数没密码，从控制台获取输入
        if (StrUtils.isEmpty(pwd)) {
            Log.debug("无法在参数中获取密码，从控制台获取");
            Console console = System.console();
            if (console != null) {
                Log.debug("控制台输入");
                pwd = console.readPassword("Password:");
            }
        }

        //不支持控制台输入，弹出gui输入
        if (StrUtils.isEmpty(pwd)) {
            Log.debug("无法从控制台中获取密码，GUI输入");
            InputForm input = new InputForm();
            boolean gui = input.showForm();
            if (gui) {
                Log.debug("GUI输入");
                pwd = input.nextPasswordLine();
                input.closeForm();
            }
        }

        //不支持gui，读取密码配置文件
        if (StrUtils.isEmpty(pwd)) {
            Log.debug("无法从GUI中获取密码，读取密码文件");
            pwd = readPasswordFromFile(options);
        }

        //还是没有获取密码，退出
        if (StrUtils.isEmpty(pwd)) {
            Log.println("\nERROR: Startup failed, could not get the password.\n");
            System.exit(0);
        }

        if (inst != null) {
            AgentTransformer tran = new AgentTransformer(pwd);
            inst.addTransformer(tran);
        }
    }

    /**
     * 读取隐藏的密码
     *
     * @return 是否
     */
    public static char[] readJarPassword() {
        String path = ClassUtils.getRootPath();
        return readJarPassword(path);
    }

    /**
     * 读取隐藏的密码
     *
     * @return 是否
     */
    public static char[] readJarPassword(String path) {
        String classFile = "META-INF/" + Const.FILE_NAME + "/" + Const.FLAG_PASS;
        File workDir = new File(path);
        byte[] passbyte = null;
        if (workDir.isFile()) {
            passbyte = JarUtils.getFileFromJar(new File(path), classFile);
        } else {//war解压的目录
            File file = new File(workDir, classFile);
            if (file.exists()) {
                passbyte = IoUtils.readFileToByte(file);
            }
        }

        if (passbyte != null) {
            char[] pass = new char[passbyte.length];
            for (int i = 0; i < passbyte.length; i++) {
                pass[i] = (char) passbyte[i];
            }
            return EncryptUtils.md5(pass);
        }
        return null;

    }

    /**
     * 从文件读取密码
     *
     * @return 密码
     */
    public static char[] readPasswordFromFile(CmdLineOption options) {
        String path = ClassUtils.getRootPath();
        if (!path.endsWith(".jar")) {
            return null;
        }
        String jarName = path.substring(path.lastIndexOf("/") + 1);
        path = path.substring(0, path.lastIndexOf("/") + 1);
        String configName = jarName.substring(0, jarName.length() - 3) + "classfinal.txt";
        File config = new File(path, configName);
        if (!config.exists()) {
            config = new File(path, "classfinal.txt");
        }

        String args = null;
        if (config.exists()) {
            args = IoUtils.readTxtFile(config);
        }

        if (StrUtils.isEmpty(args)) {
            Log.println("\nCould not get the password.");
            Log.println("You can write the password(-pwd 123456 -del true) into the '" + path + "classfinal.txt' or '" + path + configName + "'.");
            return null;
        }
        if (!args.contains(" ")) {
            return args.toCharArray();
        }

        options.parse(args.trim().split(" "));
        char[] pwd = options.getOptionValue("pwd", "").toCharArray();
        Const.DEBUG = options.hasOption("debug");

        //删除文件中的密码
        if (!"false".equalsIgnoreCase(options.getOptionValue("del"))
                && !"no".equalsIgnoreCase(options.getOptionValue("del"))) {
            args = "";
            IoUtils.writeTxtFile(config, args);
        }
        return pwd;
    }

}
