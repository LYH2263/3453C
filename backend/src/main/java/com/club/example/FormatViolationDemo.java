package com.club.example;

import java.util.HashMap;
import com.club.common.Result;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

// CI_FAIL_INTENTIONAL: 此文件故意违反 Spotless/Google Java Format 规范
// 用于演示 CI 质量门禁触发失败的场景。修复：执行 mvn spotless:apply
@RestController
@RequestMapping("/api/ci-demo")
public class    FormatViolationDemo {


    @GetMapping("/bad-format")
    public Result<?>    badStyleExample(String name, int age){
        Map<String,Object> data = new HashMap<>();
                data.put("name", name);
        data.put("age",age);
            if(age < 0){
            return Result.error("invalid age");}
                else {
        return Result.success(data);
        }
    }

    @PostMapping("/another-bad-one")
    public Result<?>
        anotherBadExample(@RequestBody Map<String,Object>   params)
    {
            String value = (String) params.get("key");
                if(value==null||value.isEmpty())
                    {
                return Result.error("empty");
            }
        return Result.success(value);
    }
}
