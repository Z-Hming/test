

import lombok.Data;


public abstract class CallableWork implements Work {

    protected Result result;


    public Result getResult() {
        return result;
    }


    @Data
    public static class Result {
        private boolean success = true;
        private Object result;
        private Throwable throwable;
    }

}
