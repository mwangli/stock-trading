package online.mwang.stockTrading.web.utils;

// 处理器接口
abstract class Handler {
    protected Handler nextHandler;

    public void setNextHandler(Handler nextHandler) {
        this.nextHandler = nextHandler;
    }

    public abstract void handleRequest(String request);
}

// 具体处理器A
class ConcreteHandlerA extends Handler {
    @Override
    public void handleRequest(String request) {
        if ("A".equals(request)) {
            System.out.println("ConcreteHandlerA handled request: " + request);
        } else if (nextHandler != null) {
            nextHandler.handleRequest(request);
        } else {
            System.out.println("No handler for request: " + request);
        }
    }
}

// 具体处理器B
class ConcreteHandlerB extends Handler {
    @Override
    public void handleRequest(String request) {
        if ("B".equals(request)) {
            System.out.println("ConcreteHandlerB handled request: " + request);
        } else if (nextHandler != null) {
            nextHandler.handleRequest(request);
        } else {
            System.out.println("No handler for request: " + request);
        }
    }
}

// 客户端
public class Main {
    public static void main(String[] args) {
        Handler handlerA = new ConcreteHandlerA();
        Handler handlerB = new ConcreteHandlerB();

        handlerA.setNextHandler(handlerB);

        handlerA.handleRequest("A"); // Output: ConcreteHandlerA handled request: A
        handlerA.handleRequest("B"); // Output: ConcreteHandlerB handled request: B
        handlerA.handleRequest("C"); // Output: No handler for request: C
    }
}
