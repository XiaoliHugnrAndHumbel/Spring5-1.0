package com.gupaoedu.hand;

import com.gupaoedu.mvcframework.annotation.GPRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class Handler {
    public Object controller;//保存方法对应的实例
    public Method method;//保存映射的方法
    public Pattern pattern;
    public Map<String,Integer> paramIndexMapping;//参数顺序

    /**
     *构造一个Handler基本的参数
     * @param controller
     * @param method
     */
    public Handler(Pattern pattern,Object controller, Method method) {

        this.controller = controller;
        this.method = method;
        this.pattern = pattern;
        paramIndexMapping = new HashMap<String,Integer>();
        putParamIndexMapping(method);

    }

    private void putParamIndexMapping(Method method) {
        // TODO Auto-generated method stub
        //提取方法中加了注解的参数
        Annotation[] [] pa = method.getParameterAnnotations();

        for (int i = 0; i < pa.length; i++) {

            for (Annotation a : pa[i]) {
                if(a instanceof GPRequestParam){
                    String paramName = ((GPRequestParam) a).value();
                    if(!"".equals(paramName.trim())){
                        paramIndexMapping.put(paramName, i);
                    }
                }

            }
        }

        //提取方法中的 request 和 response 参数
        Class<?> [] paramsTypes = method.getParameterTypes();
        for (int i = 0; i < paramsTypes.length ; i ++) {
            Class<?> type = paramsTypes[i];
            if(type == HttpServletRequest.class ||
                    type == HttpServletResponse.class){
                paramIndexMapping.put(type.getName(),i);
            }

        }


    }
    public Class<?>[] getParamTypes() {
        Class<?> [] paramsTypes = method.getParameterTypes();
        return paramsTypes;
    }
}
