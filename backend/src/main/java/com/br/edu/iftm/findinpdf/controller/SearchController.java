package com.br.edu.iftm.findinpdf.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api")
public class SearchController {

    @GetMapping("/health")
    public String health() {
        return "OK - Aplicação rodando!";
    }
}
