package com.offerget.service;

import com.offerget.dto.profile.ChangePasswordRequest;
import com.offerget.dto.profile.ProfileVO;
import com.offerget.dto.profile.UpdateProfileRequest;

public interface ProfileService {

    ProfileVO detail();

    ProfileVO update(UpdateProfileRequest request);

    void changePassword(ChangePasswordRequest request);

}
