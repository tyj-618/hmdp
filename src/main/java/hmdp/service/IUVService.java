package hmdp.service;

import hmdp.dto.Result;

public interface IUVService {
    Result recordUV();

    Result countUV(String date);
}
