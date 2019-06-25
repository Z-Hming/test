

import java.util.concurrent.Future;


public interface Worker {
     boolean execute(Work work);
     Future<CallableWork.Result> submitCallable(CallableWork work);
}
