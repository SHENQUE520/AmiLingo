package com.amilingo.platform.module.paper.controller;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
@Order(-100) @RestControllerAdvice(basePackages={"com.amilingo.platform.module.paper.controller","com.amilingo.platform.module.patent.controller","com.amilingo.platform.module.research.controller"})
public class AcademicHttpErrors {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> status(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason()==null?"请求被拒绝":e.getReason()));}
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<?> forbidden(SecurityException e){return ResponseEntity.status(403).body(Map.of("message",e.getMessage()));}
}
