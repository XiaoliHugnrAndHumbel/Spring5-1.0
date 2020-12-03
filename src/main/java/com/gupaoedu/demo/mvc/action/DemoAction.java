package com.gupaoedu.demo.mvc.action;


import com.gupaoedu.demo.service.IDemoService;
import com.gupaoedu.mvcframework.annotation.GPAutowired;
import com.gupaoedu.mvcframework.annotation.GPController;
import com.gupaoedu.mvcframework.annotation.GPRequestMapping;
import com.gupaoedu.mvcframework.annotation.GPRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@GPController
@GPRequestMapping("/demo")
public class DemoAction {
    @GPAutowired
    private IDemoService demoService;

    @GPRequestMapping("/query")
    public void query(HttpServletRequest request, HttpServletResponse response, @GPRequestParam("name")String name){
        String reString=demoService.get(name);
        try{
            response.getWriter().write(reString);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    @GPRequestMapping("/add")
    public void add(HttpServletRequest request, HttpServletResponse response, @GPRequestParam("a")Integer a,@GPRequestParam("b")Integer b){
        try{
            response.getWriter().write(a+"+"+b+"="+(a+b));
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    @GPRequestMapping("/remove")
    public void remove(HttpServletRequest req, HttpServletResponse rsp, @GPRequestParam("id") String id){

    }
}
