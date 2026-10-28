package com.offerget.service;

import java.util.List;

import com.offerget.dto.dashboard.DashboardVO;

public interface DashboardService {

    DashboardVO overview();

    List<DashboardVO.CalendarEvent> calendar(int year, int month);

}
