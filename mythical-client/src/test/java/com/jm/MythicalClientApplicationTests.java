package com.jm;

import cn.hutool.json.JSONUtil;
import com.jm.svc.IK8sPodService;
import io.fabric8.kubernetes.api.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

@SpringBootTest
class MythicalClientApplicationTests {

    @Autowired
    IK8sPodService ik8sPodService;

    @Test
    void contextLoads() {
    }


    @Test
    public void test() {
        ObjectMeta metadata = new ObjectMeta();
        metadata.setNamespace("jinmu");
        metadata.setName("tomcat-test");

        List<Container> containers = new ArrayList<>();
        PodSpec podSpec = new PodSpec();

        Container container = new Container();
        container.setName("tomcat");
        container.setImage("tomcat");
        containers.add(container);

        podSpec.setContainers(containers);
        podSpec.setServiceAccount("default");

        PodStatus podStatus = new PodStatus();

        PodBuilder podBuilder = new PodBuilder()
                .withKind("Pod")
                .withSpec(podSpec)
                .withStatus(podStatus);


        Pod pod = ik8sPodService.create("jinmu", podBuilder);

        System.out.println(JSONUtil.toJsonStr(pod));
    }

    @Test
    public void getLog() {
        String log = ik8sPodService.log("jinmu", "tomcat-test");
        System.out.println(log);


    }
}
