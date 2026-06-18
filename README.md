# xjar-springboot3

基于 core-lib/xjar 的 Spring Boot 3.5.15（JDK 21）适配版本。

---

### Spring Boot JAR 安全加密运行工具, 同时支持的原生JAR.

### 基于对JAR包内资源的加密以及拓展ClassLoader来构建的一套程序加密启动, 动态解密运行的方案, 避免源码泄露以及反编译.

## 功能特性

* 无代码侵入, 只需要把编译好的JAR包通过工具加密即可.
* 完全内存解密, 降低源码以及字节码泄露或反编译的风险.
* 支持所有JDK内置加解密算法.
* 可选择需要加解密的字节码或其他资源文件.
* 支持Maven插件, 加密更加便捷.
* 动态生成Go启动器, 保护密码不泄露.

## 环境依赖

JDK 21+

## 使用步骤

#### 1. 添加依赖

```xml

<project>
    <!-- 设置 jitpack.io 仓库 -->
    <repositories>
        <repository>
            <id>jitpack.io</id>
            <url>https://jitpack.io</url>
        </repository>
    </repositories>
    <!-- 添加 XJar 依赖 -->
    <dependencies>
        <dependency>
            <groupId>com.github.cnscottluo</groupId>
            <artifactId>xjar-springboot3-maven-plugin</artifactId>
            <version>3.5.15</version>
            <!-- <scope>test</scope> -->
        </dependency>
    </dependencies>
</project>
```

* 必须添加 https://jitpack.io Maven仓库.
* 如果使用 JUnit 测试类来运行加密可以将 XJar 依赖的 scope 设置为 test.

#### 2. 加密源码

```java
XCryptos.encryption()
        .from("/path/to/read/plaintext.jar")
        .use("io.xjar")
        .include("/io/xjar/**/*.class")
        .include("/mapper/**/*Mapper.xml")
        .exclude("/static/**/*")
        .exclude("/conf/*")
        .to("/path/to/save/encrypted.jar");
```

<table>
<thead>
    <tr>
        <th>方法名称</th><th>参数列表</th><th>是否必选</th><th>方法说明</th>
    </tr>
</thead>
<tbody>
    <tr>
        <td>from</td><td>(String jar)</td><td rowspan="2">二选一</td><td>指定待加密JAR包路径</td>
    </tr>
    <tr>
        <td>from</td><td>(File jar)</td><td>指定待加密JAR包文件</td>
    </tr>
    <tr>
        <td>use</td><td>(String password)</td><td rowspan="2">二选一</td><td>指定加密密码</td>
    </tr>
    <tr>
        <td>use</td><td>(String algorithm, int keysize, int ivsize, String password)</td><td>指定加密算法及加密密码</td>
    </tr>
    <tr>
        <td>include</td><td>(String ant)</td><td>可多次调用</td><td>指定要加密的资源相对于classpath的ANT路径表达式</td>
    </tr>
    <tr>
        <td>include</td><td>(Pattern regex)</td><td>可多次调用</td><td>指定要加密的资源相对于classpath的正则路径表达式</td>
    </tr>
    <tr>
        <td>exclude</td><td>(String ant)</td><td>可多次调用</td><td>指定不加密的资源相对于classpath的ANT路径表达式</td>
    </tr>
    <tr>
        <td>exclude</td><td>(Pattern regex)</td><td>可多次调用</td><td>指定不加密的资源相对于classpath的正则路径表达式</td>
    </tr>
    <tr>
        <td>to</td><td>(String xJar)</td><td rowspan="2">二选一</td><td>指定加密后JAR包输出路径, 并执行加密.</td>
    </tr>
    <tr>
        <td>to</td><td>(File xJar)</td><td>指定加密后JAR包输出文件, 并执行加密.</td>
    </tr>
</tbody>
</table>

* 指定加密算法的时候密钥长度以及向量长度必须在算法可支持范围内, 具体加密算法的密钥及向量长度请自行百度或谷歌.
* include 和 exclude 同时使用时即加密在include的范围内且排除了exclude的资源.

#### 3. 编译脚本

```shell script
go build xjar.go
```

* 通过步骤2加密成功后XJar会在输出的JAR包同目录下生成一个名为 xjar.go 的的Go启动器源码文件.
* 将 xjar.go 在不同的平台进行编译即可得到不同平台的启动器可执行文件, 其中Windows下文件名为 xjar.exe 而Linux下为 xjar.
* 用于编译的机器需要安装 Go 环境, 用于运行的机器则可不必安装 Go 环境, 具体安装教程请自行搜索.
* 由于启动器自带JAR包防篡改校验, 故启动器无法通用, 即便密码相同也不行.

#### 4. 启动运行

```shell script
/path/to/xjar /path/to/java [OPTIONS] -jar /path/to/encrypted.jar [ARGS]

/path/to/xjar /path/to/javaw [OPTIONS] -jar /path/to/encrypted.jar [ARGS]

nohup /path/to/xjar /path/to/java [OPTIONS] -jar /path/to/encrypted.jar [ARGS]
```

* 在 Java 启动命令前加上编译好的Go启动器可执行文件名(xjar)即可启动运行加密后的JAR包.
* 若使用 nohup 方式启动则 nohup 要放在Go启动器可执行文件名(xjar)之前.
* 若Go启动器可执行文件名(xjar)不在当前命令行所在目录则要通过绝对路径或相对路径指定.
* 仅支持通过 -jar 方式启动, 不支持-cp或-classpath的方式.
* -jar 后面必须紧跟着启动的加密jar文件路径
* 例子: 如果当前命令行就在 xjar 所在目录, java 环境变量也设置好了 ./xjar java -Xms256m -Xmx1024m -jar /path/to/encrypted.jar

## 注意事项

#### 1. 不兼容 spring-boot-maven-plugin 的 executable = true 以及 embeddedLaunchScript

```xml

<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <!-- 需要将executable和embeddedLaunchScript参数删除, 目前还不能支持对该模式Jar的加密！后面可能会支持该方式的打包. 
    <configuration>
        <executable>true</executable>
        <embeddedLaunchScript>...</embeddedLaunchScript>
    </configuration>
    -->
</plugin>
```

#### 2. Spring Boot + JPA(Hibernate) 启动报错问题

如果项目中使用了 JPA 且实现为Hibernate时, 由于Hibernate自己解析加密后的Jar文件, 所以无法正常启动, 可以采用以下解决方案:

1. clone [XJar-Agent-Hibernate](https://github.com/core-lib/xjar-agent-hibernate) , 使用 mvn clean package 编译出 xjar-agent-hibernate-$
   {version}.jar 文件
2. 采用 xjar java -javaagent:xjar-agent-hibernate-${version}.jar -jar your-spring-boot-app.jar 命令启动

#### 3. 静态文件浏览器无法加载完成问题

由于静态文件被加密后文件体积变大, Spring Boot 会采用文件的大小作为 Content-Length 头返回给浏览器,
但实际上通过 XJar 加载解密后文件大小恢复了原本的大小, 所以浏览器认为还没接收完导致一直等待服务端.
由此我们需要在加密时忽略静态文件的加密, 实际上静态文件也没加密的必要, 因为即便加密了用户在浏览器
查看源代码也是能看到完整的源码.通常情况下静态文件都会放在 static/ 和 META-INF/resources/ 目录下,
我们只需要在加密时通过 exclude 方法排除这些资源即可, 可以参考以下例子：

```java
XCryptos.encryption()
    .from("/path/to/read/plaintext.jar")
    .use("io.xjar")
    .exclude("/static/**/*")
    .exclude("/META-INF/resources/**/*")
    .to("/path/to/save/encrypted.jar");
```

#### 4. JDK-9及以上版本由于模块化导致XJar无法使用 jdk.internal.loader 包的问题解决方案

在启动时添加参数 --add-opens java.base/jdk.internal.loader=ALL-UNNAMED

```shell script
xjar java --add-opens java.base/jdk.internal.loader=ALL-UNNAMED -jar /path/to/encrypted.jar
```

#### 5. 由于使用了阿里云Maven镜像导致无法从 jitpack.io 下载 XJar 依赖的问题

参考如下设置, 在镜像配置的 mirrorOf 元素中加入 ,!jitpack.io 结尾.

```xml

<mirror>
    <id>alimaven</id>
    <mirrorOf>central,!jitpack.io</mirrorOf>
    <name>aliyun maven</name>
    <url>http://maven.aliyun.com/nexus/content/repositories/central/</url>
</mirror>
```