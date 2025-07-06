打开命令行

## 先运行 Server
```cmd

mvn clean compile exec:java -Dexec.mainClass=org.example.Server

```
## 然后是 ServerDashboard
```cmd
mvn clean compile javafx:run@dashboard

```

## 最后是ClientGUI
```cmd
mvn clean compile javafx:run@client

```

## 查看端口占用

```cmd
tasklist | findstr 9999

```