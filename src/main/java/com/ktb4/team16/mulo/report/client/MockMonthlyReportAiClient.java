package com.ktb4.team16.mulo.report.client;
import java.util.List; import org.springframework.stereotype.Component;
@Component public class MockMonthlyReportAiClient implements MonthlyReportAiClient { public Result generate(int year,int month,String artist,boolean hasPhotos){return new Result(year+"년 "+month+"월 "+(artist==null?"음악과 함께한":""+artist+"의 음악과 함께한")+" 기록이에요.", hasPhotos?List.of(new Scene("일상",1,100)):List.of());} }
