package com.hello.core.lifecycle;

/*
    - 데이터베이스 커넥션 풀이나, 네트워크 소켓처럼 애플리케이션 시작 시점에 필요한 연결을 미리 해두고,
    - 애플리케이션 종료 시점에 연결을 모두 종료하는 작업을 진행하려면,
    - 객체의 초기화와 종료 작업이 필요하다.
    - 아래의 `NetworkClient`는 애플리케이션 시작 시점에 `connect()`를 호출해서 연결을 맺어두어야 하고,
    - 애플리케이션이 종료되면 `disConnnect()`를 호출해서 연결을 끊어야 한다.
 */

/*
 - 생성자 부분을 보면 url 정보 없이 connect가 호출되는 것을 확인할 수 있다.
 - 너무 당연한 이야기이지만 객체를 생성하는 단계에는 url이 없고, 객체를 생성한 다음에 외부에서 수정자 주입을 통해서 setUrl()이 호출되어야 url이 존재하게 된다.
 - 스프링 빈은 간단하게 **객체 생성 -> 의존관계 주입** 과 같은 라이프사이클을 가진다.
 - 스프링 빈은 객체를 생성하고, 의존관계 주입이 다 끝난 다음에야 필요한 데이터를 사용할 수 있는 준비가 완료된다.
 - 따라서 초기화 작업은 의존관계 주입이 모두 완료되고 난 다음에 호출해야 한다. 그런데 개발자가 의존관계 주입이 모두 완료된 시점을 어떻게 알 수 있을까?
 - 스프링은 의존관계 주입이 완료되면 스프링 빈에게 콜백 메서드를 통해서 초기화 시점을 알려주는 다양한 기능을 제공한다.
 - 또한 스프링은 스프링 컨테이너가 종료되기 직전에 소멸 콜백을 준다. 따라서 안전하게 종료 작업을 진행할 수 있다.
 - 스프링 빈의 이벤트 라이프사이클
    - 스프링 컨테이너 생성 -> 스프링 빈 생성 -> 의존관계 주입 -> 초기화 콜백 -> 사용 -> 소멸전 콜백 -> 스프링 종료
 - 객체의 생성과 초기화는 분리해야 한다.
 - 생성자는 필수 정보를 받고, 메모리를 할당해서 객체를 생성하는 책임을 가진다. 반면에 초기화는 이렇게 생성된 값들을 활용해서 외부 커넥션을 연결하는등 무거운 동작을 수행한다.
 - 따라서 생성자 안에서 무거운 초기화 작업을 함께 하는 것 보다는 객체를 생성하는 부분과 초기화 하는 부분을 명확하게 나누는 것이 유지보수 관점에서 좋다.
 - 물론 초기화 작업이 내부 값들만 약간 변경하는 정도로 단순한 경우에는 생성자에서 한번에 다 처리하는게 더 나을 수 있다.
    - 생성자 안에 주입해도 되는것들은 초기 값을 셋팅 하는 정도만 권장
    - 무거운 작업은 별도 초기화 메서드 활용하는것이 좋다.

 - 스프링은 크게 3가지 방법으로 빈 생명주기 콜백을 지원한다.
    - 인터페이스(InitializingBean, DisposableBean)
        - InitializingBean -> afterPropertiesSet
        - DisposableBean -> destory
        - 초기화, 소멸 인터페이스 단점
        1. 해당 인터페이스는 스프링 전용 인터페이스이다. 해당 코드가 스프링 전용 인터페이스에 의존한다.
        2. 초기화, 소멸 메서드의 이름을 변경할 수 없다.
        3. 내가 코드를 고칠 수 없는 외부 라이브러리에 적용할 수 없다.
        - 인터페이스를 사용하는 초기화, 종료 방법은 스프링 초창기에 나온 방법이고, 지금은 아래의 더 나은 방법들이 있어서 거의 사용하지 않는다.

    - 설정 정보에 초기화 메서드, 종료 메서드 지정
        - 설정 정보에 `@Bean(initMethod = "init", destoryMethod = "close") 처럼 초기화, 소멸 메서드를 지정할 수 있다.
        ```
        @Configuration
        static class LifeCycleConfig {
            @Bean(initMethod = "init", destroyMethod = "close")
            public NetworkClient networkClient() {
                NetworkClient networkClient = new NetworkClient();

                networkClient.setUrl("http://www.example.com");

                return networkClient;
            }
         }
        ```
        - 메서드 이름을 자유롭게 줄 수 있다.
        - 스프링 빈이 스프링 코드에 의존하지 않는다.
        - 코드가 아니라 설정 정보를 사용하기 때문에 코드를 고칠 수 없는 외부 라이브러리에도 초기화, 종료 메서드를 적용할 수 있다.

    - @PostConstruct, @PreDestory 애노테이션 지원
        - 최신 스프링에서 가장 권장하는 방법
        - 애노테이션 하나만 붙이면 되므로 매우 편리하다.
        - 스프링에 종속적인 기술이 아니라 JSR-250라는 자바 표준이다. 따라서 스프링이 아닌 다른 컨테이너에서도 동작한다.
        - 컴포넌트 스캔과 잘 어울린다.
        - 유일한 단점은 외부 라이브러리에는 적용하지 못한다는 것이다.
        - 외부 라이브러리를 초기화, 종료 해야 하면 @Bean의 기능을 사용
    - 결론) @PostConstruct, @PreDestory 애노테이션을 사용
    - 코드를 고칠 수 없는 외부 라이브러리를 초기화, 종료해야 하면 @Bean의 initMethod, destoryMethod를 사용하자.
 */

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

public class NetworkClient {
    private String url;

    public NetworkClient() {
        System.out.println("생성자 호출, url = " + url);
    }

    public void setUrl(String url) {
        this.url = url;
    }

    // 서비스를 시작시 호출
    public void connect() {
        System.out.println("connect = " + url);
    }

    public void call(String message) {
        System.out.println("call: " + url + " message = " + message);
    }

    // 서비스 종료시 호출
    public void disConnect() {
        System.out.println("close = " + url);
    }

    @PostConstruct
    public void init() {
        System.out.println("NetworkClient.afterPropertiesSet");
        connect();
        call("초기화 연결 메시지");
    }

    @PreDestroy
    public void close() {
        System.out.println("NetworkClient.destroy");
        disConnect();
    }
}
