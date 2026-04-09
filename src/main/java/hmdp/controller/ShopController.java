package hmdp.controller;

import hmdp.entity.Shop;
import hmdp.service.IShopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shop")
public class ShopController {

    @Autowired
    private IShopService shopService;

    @GetMapping("/{id}")
    public Shop queryShopById(@PathVariable("id") Long id) {
        return shopService.getById(id);
    }
}
