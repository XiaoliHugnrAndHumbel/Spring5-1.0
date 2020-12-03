package com.gupaoedu.mvcframework.v1.servlet;

import com.gupaoedu.hand.Handler;
import com.gupaoedu.mvcframework.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GPDispatcherServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    //保存application.properties配置文件中的内容
    private Properties configContext=new Properties();
    //保存扫描的所有的类名
    private List<String> classNames=new ArrayList<String>();

    //ioc容器
    private Map<String,Object> ioc=new HashMap<String, Object>();

    //保存url和methon的对应关系
    private List<Handler> handlerMapping=new ArrayList<Handler>();


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // TODO Auto-generated method stub
        System.out.println(req.getRequestURI());
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // TODO Auto-generated method stub
        try {
            doDispatch(req, resp);
            System.out.println(req.getRequestURI());
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("500 Exception " + Arrays.toString(e.getStackTrace()));
        }
    }


    /**
     * 解析请求，获取url，根据url获取对应controller方法
     *通过反射执行controller方法
     * @param req
     * @param resp
     * @throws Exception
     */
    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        // TODO Auto-generated method stub
        Handler handler=getHandler(req);
        if (handler==null){
            resp.getWriter().write("404 not found");
            return;
        }

        //获得方法的形参列表
        Class<?> [] paramTypes = handler.getParamTypes();

        Object [] paramValues = new Object[paramTypes.length];

        Map<String,String[]> params = req.getParameterMap();

        for (Map.Entry<String, String[]> parm : params.entrySet()) {

            String value = Arrays.toString(parm.getValue()).replaceAll("\\[|\\]","")
                    .replaceAll("\\s",",");
            if(!handler.paramIndexMapping.containsKey(parm.getKey())){continue;}
            int index = handler.paramIndexMapping.get(parm.getKey());
            paramValues[index] = convert(paramTypes[index],value);
        }

        if(handler.paramIndexMapping.containsKey(HttpServletRequest.class.getName())) {
            int reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
            paramValues[reqIndex] = req;
        }

        if(handler.paramIndexMapping.containsKey(HttpServletResponse.class.getName())) {
            int respIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
            paramValues[respIndex] = resp;
        }

        Object returnValue = handler.method.invoke(handler.controller,paramValues);

        if(returnValue == null || returnValue instanceof Void){ return; }

        resp.getWriter().write(returnValue.toString());

    }

    /**
     * 核心方法
     *所有bean的扫描，以及加入到容器中
     * 找到url对应controller方法，存到容器中
     * 自动装配GPAutowired属性
     * @param config
     * @throws ServletException
     */
    @Override
    public void init(ServletConfig config) throws ServletException {
        //加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        //扫描相关类
        doScanner(configContext.getProperty("scanPackage"));
        //初始化扫描到的类，并且将他们放入ioc容器中
        doInstance();
        //完成依赖注入
        doAutowired();
        //初始化HandlerMapping
        initHandlerMapping();

        System.out.println("GP Spring framework is init");
    }

    //读取配置文件
    private void doLoadConfig(String contextConfigLocation){
        //通过类路径找到spring主配置文件所在的路径，读取配置文件中内容保存到内存中
        InputStream fis =this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try{
            configContext.load(fis);
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            if(null!=fis){
                try{
                    fis.close();
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }
    }
    private void doInstance(){
        //初始化，为DI做准备
        if (classNames.isEmpty())return;

        try{
            for(String className:classNames){
                Class<?> clazz=Class.forName(className);
                //是否有指定的注解
                if (clazz.isAnnotationPresent(GPController.class)) {
                    Object instance = clazz.newInstance();
                    String beanname = toLowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanname, instance);
                }else if (clazz.isAnnotationPresent(GPService.class)){
                    GPService service=clazz.getAnnotation(GPService.class);
                    String beanname=service.value();
                    if ("".equals(beanname.trim())){
                        beanname=toLowerFirstCase(clazz.getSimpleName());//如果GPService没有指定对应的beanname，则用类名表示
                    }
                    Object instance=clazz.newInstance();
                    ioc.put(beanname,instance);
                    for(Class<?> i:clazz.getInterfaces()){
                        if (ioc.containsKey(i.getName())){
                            throw new Exception("the “"+i.getName()+"” is exists！！");
                        }
                        ioc.put(i.getName(),instance);
                    }
                }
                else{
                    continue;
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    //自动进行依赖注入
    private void doAutowired(){
        if(ioc.isEmpty()){return;}

        for(Map.Entry<String,Object> entry:ioc.entrySet()){
            Field[] fields=entry.getValue().getClass().getDeclaredFields();
            for(Field field: fields){
                if (!field.isAnnotationPresent(GPAutowired.class)){
                    continue;
                }
                GPAutowired autowired=field.getAnnotation(GPAutowired.class);

                String beanname=autowired.value().trim();
                if ("".equals(beanname)){
                        beanname=field.getType().getName();
                }

                //如果是pyblic以外的类型，只要加了@Autowired注解都要强制赋值
                //反射中的暴力访问
                field.setAccessible(true);
                try{
                    //用反射机制动态给字段赋值
                    field.set(entry.getValue(),ioc.get(beanname));
                }catch (Exception e){
                    e.printStackTrace();
                }

            }
        }
    }
    //初始化url和method的一对一关系
    private void initHandlerMapping(){
        if (ioc.isEmpty())return;
        for (Map.Entry<String,Object> entry:ioc.entrySet()){
            Class<?> clazz =entry.getValue().getClass();
            if (!clazz.isAnnotationPresent(GPController.class)){
                continue;
            }

            String baseUrl="";
            if (clazz.isAnnotationPresent(GPRequestMapping.class)){
                GPRequestMapping requestMapping=clazz.getAnnotation(GPRequestMapping.class);
                baseUrl=requestMapping.value();
            }

            //默认获取所有的public方法
            for(Method method:clazz.getMethods()){
                if (!method.isAnnotationPresent(GPRequestMapping.class)){
                    continue;
                }
                GPRequestMapping requestMapping=method.getAnnotation(GPRequestMapping.class);
                String url=(baseUrl+requestMapping.value()).replaceAll("/+","/");
                Pattern pattern=Pattern.compile(url);
                handlerMapping.add(new Handler(pattern,entry.getValue(),method));
                System.out.println("Mapped :"+url+","+method);
            }
        }
    }

    //扫描相关的类
    private void doScanner(String scanPackage) {//扫描配置文件下的所有class，并将相对路径加入map中
        //将.转换为/  其实就是将包路径转换为路径地址
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.", "/"));
        File classDir = new File(url.getFile());
        for (File file : classDir.listFiles()) {
            if (file.isDirectory()) {
                doScanner(scanPackage + "." + file.getName());
            } else {
                if (!file.getName().endsWith(".class")) {
                    continue;
                }
                String clazzName = (scanPackage + "." + file.getName().replace(".class", ""));
                classNames.add(clazzName);
            }
        }
    }
    private Handler getHandler(HttpServletRequest req) {
        if(handlerMapping.isEmpty()){ return null; }
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replace(contextPath, "").replaceAll("/+", "/");
        for (Handler  handler : handlerMapping) {
            Matcher matcher = handler.pattern.matcher(url);
            System.out.println(matcher.matches());
            //如果没有匹配上继续下一个匹配
            if(!matcher.matches()){ continue; }
            return handler;
        }
        return null;
    }

    //类型的强制转换
    private Object convert(Class<?> type,String value){
        if(Integer.class == type){
            return Integer.valueOf(value);
        }
        return value;
    }

    //将首字母转为小写
    private String toLowerFirstCase(String simpleName){
        char[] chars=simpleName.toCharArray();
        //之所以做加法，是因为大小写字母在ascll码中相差32
        chars[0]+=32;
        return String.valueOf(chars);
    }
}
