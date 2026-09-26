package com.ktb4.team16.mulo.record.service;

import com.ktb4.team16.mulo.music.entity.MusicTrack;
import com.ktb4.team16.mulo.music.service.MusicTrackService;
import com.ktb4.team16.mulo.place.entity.Place;
import com.ktb4.team16.mulo.place.repository.PlaceRepository;
import com.ktb4.team16.mulo.record.cursor.RecordCursor;
import com.ktb4.team16.mulo.record.cursor.RecordCursorCodec;
import com.ktb4.team16.mulo.record.cursor.RecordCursorPagination;
import com.ktb4.team16.mulo.record.dto.request.RecordCreateRequest;
import com.ktb4.team16.mulo.record.dto.response.MyPlaceRecordResponseDto;
import com.ktb4.team16.mulo.record.dto.response.MyPlaceRecordsResponseDto;
import com.ktb4.team16.mulo.record.dto.response.RecordCommentUpdateResponse;
import com.ktb4.team16.mulo.record.dto.response.RecordCreateResponse;
import com.ktb4.team16.mulo.record.dto.response.RecordDetailData;
import com.ktb4.team16.mulo.record.dto.response.RecordRegionGroupResponse;
import com.ktb4.team16.mulo.record.dto.response.RecordRegionRecordsData;
import com.ktb4.team16.mulo.record.entity.Record;
import com.ktb4.team16.mulo.record.exception.InvalidRecordIdException;
import com.ktb4.team16.mulo.record.exception.RecordNotFoundException;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import com.ktb4.team16.mulo.recordphoto.entity.RecordPhoto;
import com.ktb4.team16.mulo.recordphoto.repository.RecordPhotoRepository;
import com.ktb4.team16.mulo.global.exception.UnauthenticatedUserException;
import com.ktb4.team16.mulo.upload.entity.Upload;
import com.ktb4.team16.mulo.upload.service.UploadService;
import com.ktb4.team16.mulo.upload.storage.GcsStorageService;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import com.ktb4.team16.mulo.weather.dto.WeatherResponse;
import com.ktb4.team16.mulo.weather.exception.WeatherApiException;
import com.ktb4.team16.mulo.weather.service.WeatherService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecordService {

    private static final int PAGE_SIZE = 20;

    private final RecordRepository recordRepository;
    private final RecordCursorCodec recordCursorCodec;
    private final UserRepository userRepository;
    private final PlaceRepository placeRepository;
    private final WeatherService weatherService;
    private final UploadService uploadService;
    private final MusicTrackService musicTrackService;
    private final RecordPhotoRepository recordPhotoRepository;
    private final GcsStorageService gcsStorageService;


    public Record findActiveRecordForOwner(Long userId, Long recordId) {
        if (recordId == null || recordId <= 0) {
            throw new InvalidRecordIdException();
        }

        return recordRepository.findByRecordIdAndUser_UserIdAndDeletedAtIsNull(recordId, userId)
                .orElseThrow(RecordNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public RecordDetailData getRecordDetail(Long userId, Long recordId) {
        Record record = findActiveRecordForOwner(userId, recordId);
        RecordPhoto recordPhoto = recordPhotoRepository.findByRecord_RecordId(record.getRecordId())
                .orElseThrow(IllegalStateException::new);
        String photoUrl = gcsStorageService.createReadSignedUrl(recordPhoto.getImageUrl());

        Place place = record.getPlace();
        MusicTrack musicTrack = record.getMusicTrack();

        return new RecordDetailData(
                record.getRecordId(),
                record.getUser().getUserId(),
                new RecordDetailData.Place(
                        place.getPlaceId(),
                        place.getLegalDongName(),
                        place.getLatitude(),
                        place.getLongitude(),
                        place.getLegalDongCode()),
                new RecordDetailData.Music(
                        musicTrack.getMusicTrackId(),
                        musicTrack.getTitle(),
                        musicTrack.getArtistName(),
                        musicTrack.getAlbumImageUrl(),
                        musicTrack.getExternalUrl()),
                record.getWeatherCondition(),
                record.getTemperature(),
                record.getMoodScore(),
                record.getComment(),
                photoUrl,
                record.getCreatedAt());
    }

    @Transactional
    public RecordCommentUpdateResponse updateRecordComment(
            Long userId,
            Long recordId,
            String comment
    ) {
        Record record = findActiveRecordForOwner(userId, recordId);
        record.updateComment(comment, LocalDateTime.now());
        return new RecordCommentUpdateResponse(record.getRecordId(), comment);
    }

    @Transactional
    public void deleteRecord(Long userId, Long recordId) {
        Record record = findActiveRecordForOwner(userId, recordId);
        record.softDelete(LocalDateTime.now());
    }

    public MyPlaceRecordsResponseDto getMyPlaceRecords(
            Long userId,
            List<Long> placeIds,
            String cursor
    ) {
        Pageable pageable = PageRequest.of(0, PAGE_SIZE + 1);

        List<MyPlaceRecordResponseDto> records;

        if (cursor == null) {
            records = recordRepository.findMyPlaceRecordsFirstPage(
                            userId,
                            placeIds,
                            pageable
                    );
        } else {
            RecordCursor decodedCursor = recordCursorCodec.decode(cursor);

            records = recordRepository.findMyPlaceRecordsAfterCursor(
                            userId,
                            placeIds,
                            decodedCursor.createdAt(),
                            decodedCursor.recordId(),
                            pageable
                    );
        }

        boolean hasNext = records.size() > PAGE_SIZE;

        List<MyPlaceRecordResponseDto> responseRecords;

        if (hasNext) {
            responseRecords = records.subList(0, PAGE_SIZE);
        } else {
            responseRecords = records;
        }

        String nextCursor = null;
        if (hasNext) {
            MyPlaceRecordResponseDto lastRecord =
                    responseRecords.get(responseRecords.size() - 1);

            nextCursor = recordCursorCodec.encode(
                    lastRecord.createdAt(),
                    lastRecord.recordId()
            );
        }

        return new MyPlaceRecordsResponseDto(
                responseRecords,
                nextCursor
        );
    }

    public List<RecordRegionGroupResponse> getMyRecordRegions(Long userId) {
        return recordRepository.findMyRecordRegions(userId).stream()
                .map(this::toApiRegionGroup)
                .toList();
    }

    public RecordRegionRecordsData getMyRecords(
            Long userId,
            String legalDongCode,
            String cursor
    ) {
        Pageable pageable = PageRequest.of(0, RecordCursorPagination.fetchSize());
        List<MyPlaceRecordResponseDto> records;

        if (cursor == null) {
            records = findRecordsByRegion(userId, legalDongCode, pageable);
        } else {
            RecordCursor decodedCursor = recordCursorCodec.decode(cursor);
            records = findRecordsByRegionAfterCursor(
                    userId,
                    legalDongCode,
                    decodedCursor,
                    pageable
            );
        }

        RecordCursorPagination.CursorPage<MyPlaceRecordResponseDto> page =
                RecordCursorPagination.paginate(
                        records,
                        recordCursorCodec,
                        MyPlaceRecordResponseDto::createdAt,
                        MyPlaceRecordResponseDto::recordId);

        Optional<RecordRegionGroupResponse> region = findRecordRegion(userId, legalDongCode);
        String responseCode = RecordRegionGroupResponse.UNKNOWN_LEGAL_DONG_CODE.equals(legalDongCode)
                ? RecordRegionGroupResponse.UNKNOWN_LEGAL_DONG_CODE
                : legalDongCode;
        String responseName = RecordRegionGroupResponse.UNKNOWN_LEGAL_DONG_CODE.equals(legalDongCode)
                ? "위치 정보 없음"
                : region.map(RecordRegionGroupResponse::legalDongName).orElse(null);
        Long recordsCount = region.map(RecordRegionGroupResponse::recordsCount).orElse(0L);

        return new RecordRegionRecordsData(
                responseCode,
                responseName,
                recordsCount,
                page.records(),
                page.nextCursor());
    }

    private Optional<RecordRegionGroupResponse> findRecordRegion(Long userId, String legalDongCode) {
        if (RecordRegionGroupResponse.UNKNOWN_LEGAL_DONG_CODE.equals(legalDongCode)) {
            return recordRepository.findMyUnknownRecordRegion(userId);
        }
        return recordRepository.findMyRecordRegion(userId, legalDongCode);
    }

    private List<MyPlaceRecordResponseDto> findRecordsByRegion(
            Long userId,
            String legalDongCode,
            Pageable pageable
    ) {
        if (RecordRegionGroupResponse.UNKNOWN_LEGAL_DONG_CODE.equals(legalDongCode)) {
            return recordRepository.findMyRecordsInUnknownRegion(userId, pageable);
        }
        return recordRepository.findMyRecordsByLegalDongCode(userId, legalDongCode, pageable);
    }

    private List<MyPlaceRecordResponseDto> findRecordsByRegionAfterCursor(
            Long userId,
            String legalDongCode,
            RecordCursor cursor,
            Pageable pageable
    ) {
        if (RecordRegionGroupResponse.UNKNOWN_LEGAL_DONG_CODE.equals(legalDongCode)) {
            return recordRepository.findMyRecordsInUnknownRegionAfterCursor(
                    userId, cursor.createdAt(), cursor.recordId(), pageable);
        }
        return recordRepository.findMyRecordsByLegalDongCodeAfterCursor(
                userId, legalDongCode, cursor.createdAt(), cursor.recordId(), pageable);
    }

    private RecordRegionGroupResponse toApiRegionGroup(RecordRegionGroupResponse regionGroup) {
        if (Objects.isNull(regionGroup.legalDongCode())) {
            return new RecordRegionGroupResponse(
                    RecordRegionGroupResponse.UNKNOWN_LEGAL_DONG_CODE,
                    "위치 정보 없음",
                    regionGroup.recordsCount());
        }
        return regionGroup;
    }


    @Transactional
    public RecordCreateResponse createRecord(
            Long userId,
            RecordCreateRequest request
    ) {
        User user = userRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(UnauthenticatedUserException::new);

        BigDecimal longitude = request.location().longitude();
        BigDecimal latitude = request.location().latitude();
        String legalDongCode = request.location().legalDongCode();
        String legalDongName = request.location().legalDongName();
        Long uploadId = request.uploadId();

        Optional<Place> existingPlace =
                placeRepository.findByLatitudeAndLongitude(
                        latitude,
                        longitude
                );

        Place place;

        if (existingPlace.isPresent()) {
            place = existingPlace.get();
        } else {
            place = new Place(
                    legalDongCode,
                    legalDongName,
                    latitude,
                    longitude
            );
            placeRepository.save(place);
        }

        Upload upload = uploadService.findValidUpload(userId, uploadId);

        BigDecimal temperature = null;
        Record.WeatherCondition weatherCondition = null;

        try {
            WeatherResponse weatherResponse = weatherService.getWeather(
                    latitude.doubleValue(),
                    longitude.doubleValue(),
                    OffsetDateTime.now()
            );

            WeatherResponse.WeatherData weatherData =
                    weatherResponse.data();

            temperature = weatherData.temperature();

            weatherCondition = Record.WeatherCondition.valueOf(
                    weatherData.weatherCondition().name()
            );

        } catch (WeatherApiException exception) {
        // 날씨 조회에 실패해도 자물쇠 생성은 계속한다.
        }

        RecordCreateRequest.Music music = request.music();

        MusicTrack musicTrack = musicTrackService.findOrCreate(
                music.externalTrackId(),
                music.title(),
                music.artistName(),
                music.albumImageUrl(),
                music.externalUrl()
        );

        Byte moodScore = request.moodScore().byteValue();
        String comment = request.comment();

        Record record = Record.create(
                user,
                place,
                musicTrack,
                weatherCondition,
                temperature,
                moodScore,
                comment,
                LocalDateTime.now()
        );

        recordRepository.save(record);

        RecordPhoto recordPhoto = RecordPhoto.create(
                record,
                upload.getImageUrl(),
                upload.getMimeType(),
                upload.getFileSize()
        );

        recordPhotoRepository.save(recordPhoto);
        uploadService.deleteMetadata(upload);

        return new RecordCreateResponse(record.getRecordId());
    }
}
