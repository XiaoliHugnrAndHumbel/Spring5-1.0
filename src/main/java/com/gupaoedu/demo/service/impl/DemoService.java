package com.gupaoedu.demo.service.impl;

import com.gupaoedu.demo.service.IDemoService;
import com.gupaoedu.mvcframework.annotation.GPService;

@GPService
public class DemoService implements IDemoService {
    public String get(String name) {
        return "MY NAME IS "+name;
    }
}
