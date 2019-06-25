package com.archly.delivery.engine.worker;

import lombok.Data;

/**
 * @author :zj.zou
 * @desc :
 * @date :2019.03.14
 */
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
