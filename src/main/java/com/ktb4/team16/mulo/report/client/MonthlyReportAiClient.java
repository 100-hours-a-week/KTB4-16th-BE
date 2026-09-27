package com.ktb4.team16.mulo.report.client;
import java.util.List;
public interface MonthlyReportAiClient { Result generate(int year,int month,String topArtistName,boolean hasPhotos); record Result(String recapText,List<Scene> scenes){} record Scene(String tag,int count,int ratio){} }
