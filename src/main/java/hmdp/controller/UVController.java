package hmdp.controller;

import hmdp.dto.Result;
import hmdp.service.IUVService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/uv")
public class UVController {

    @Resource
    private IUVService uvService;

    @PostMapping("/record")
    public Result recordUV() {
        return uvService.recordUV();
    }

    @GetMapping("/count")
    public Result countUV(@RequestParam(value = "date", required = false) String date) {
        return uvService.countUV(date);
    }
}
