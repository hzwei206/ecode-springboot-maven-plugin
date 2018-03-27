spring boot应用maven打包插件，扩展自spring-boot-maven-plugin插件，扩展功能为：  
1.对spring-boot-maven-plugin插件打包生成的可执行jar瘦身：将原可执行jar(fat jar)里的BOOT-INF\lib里的依赖jar分离出来，放到同目录lib路径下；  
2.增加配置参数，指定打包路径；

用法：    
将本插件安装到您的maven仓库(可以直接将[ecode-springboot-maven-plugin/dist/](https://github.com/hzwei206/ecode-springboot-maven-plugin/tree/master/dist)下面的内容复制到您的maven仓库)，然后修改您的springboot项目的pom.xml，将原spring-boot-maven-plugin插件替换为本插件，然后执行 【mvn clean -Dmaven.test.skip=true package】  
原spring-boot-maven-plugin插件：  
```xml  
        <plugin>
           <groupId>org.springframework.boot</groupId>
           <artifactId>spring-boot-maven-plugin</artifactId>
           <configuration>
              <executable>true</executable> 
              <mainClass>${project.mainClass}</mainClass> 
           </configuration>
           <executions>
                <execution>
                    <goals>
                        <goal>repackage</goal>
                    </goals>
                </execution>
            </executions>
        </plugin> 
```

本插件：
```xml  
        <plugin>
           <groupId>com.jsecode.springboot</groupId>
           <artifactId>ecode-springboot-maven-plugin</artifactId>
           <version>1.0</version>
           <configuration>
              <executable>true</executable> 
              <allInOne>false</allInOne>
              <mainClass>${project.mainClass}</mainClass> 
              <distDir>${project.distDir}</distDir>
           </configuration>
           <executions>
                <execution>
                    <goals>
                        <goal>repackage</goal>
                    </goals>
                </execution>
            </executions>
        </plugin> 
```

区别：（1）groupId及artifactId  
（2）configuration里增加了allInOne及distDir两个参数：  
    allInOne：true|false，true-打包效果跟原插件(spring-boot-maven-plugin)一样；false-将可执行包的BOOT-INF\lib里的依赖jar从可执行分离出来，放到可执行包同路径lib目录下面；  
    distDir：配置存放可执行包的路径，如果不配置，默认项目target路径下面；  
    
部署：将可执行包与lib依赖库及脚本（java -jar your-project-exe-jar.jar）放在同一路径，部署目录示例：  
<pre>
      ----config/
      ----your-springboot-project-exe-jar.jar  
      ----lib/  
            |-------spring-boot-1.5.10.RELEASE.jar  
            |-------spring-boot-starter-web-1.5.10.RELEASE.jar    
            |-------xxxxx.jar
      ----startup.bat/startup.sh  
      ----shutdown.bat/shutdown.sh
      ----logs/
</pre>