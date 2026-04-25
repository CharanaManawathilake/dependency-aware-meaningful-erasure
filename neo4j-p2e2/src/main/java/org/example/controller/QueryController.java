package org.example.controller;

import org.example.dto.QueryRequest;
import org.example.service.QueryInterceptorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/query")
public class QueryController {
    @Autowired
    private QueryInterceptorService interceptor;

    @PostMapping
    public Object execute(@RequestBody QueryRequest request) throws Exception {
        return interceptor.process(
                request.getQuery()
        );
    }
}
