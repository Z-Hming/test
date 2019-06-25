package com.archly.delivery.engine.worker;

import java.util.concurrent.Future;

/**
 * @author :zj.zou
 * @desc :
 * @date :2019.03.14
 */
public interface Worker {
     boolean execute(Work work);
     Future<CallableWork.Result> submitCallable(CallableWork work);
}
