package com.example.trello.domain.user.service;

import com.example.trello.domain.user.dto.ChangePasswordRequestDto;
import com.example.trello.domain.user.dto.SignupRequestDto;
import com.example.trello.domain.user.dto.UserDeleteRequestDto;
import com.example.trello.domain.user.dto.UserInfoRequestDto;
import com.example.trello.domain.user.dto.UserResponseDto;
import com.example.trello.domain.user.entity.User;
import com.example.trello.domain.user.repository.UserRepository;
import com.example.trello.global.util.JwtUtil;
import com.example.trello.global.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

	private final UserRepository userRepository;

	private final PasswordEncoder passwordEncoder;

	private final JwtUtil jwtUtil;

	private final RedisUtil redisUtil;

	public void signup(SignupRequestDto requestDto) {
		if (userRepository.findByEmail(requestDto.getEmail()).isPresent()) {
			throw new IllegalArgumentException("중복된 이름을 가진 회원이 있습니다.");
		}
		if (userRepository.findByNickname(requestDto.getNickname()).isPresent()) {
			throw new IllegalArgumentException("중복된 닉네임을 가진 회원이 있습니다.");
		}
		String password = passwordEncoder.encode(requestDto.getPassword());
		requestDto.setPassword(password);

		User user = new User(requestDto);
		userRepository.save(user);
	}

	public void updateUser(Long userId, UserInfoRequestDto requestDto) {
		User user = userRepository.findByMyId(userId).orElseThrow(
			() -> new IllegalArgumentException("계정 정보가 없습니다.")
		);
		if (userRepository.findByNickname(requestDto.getNickname()).isPresent()
			&& !user.getNickname().equals(requestDto.getNickname())) {
			throw new IllegalArgumentException("중복된 닉네임을 가진 회원이 있습니다.");
		}
		user.update(requestDto);
		userRepository.update(user);
	}

	public void changePassword(Long userId, ChangePasswordRequestDto requestDto) {
		User user = userRepository.findByMyId(userId).orElseThrow(
			() -> new IllegalArgumentException("계정 정보가 없습니다.")
		);
		if (!passwordEncoder.matches(requestDto.getExistingPassword(), user.getPassword())) {
			throw new IllegalArgumentException("비밀번호가 틀렸습니다.");
		}
		String password = passwordEncoder.encode(requestDto.getNewPassword());
		user.changePassword(password);
		userRepository.update(user);
	}

	public void deleteUser(Long userId, UserDeleteRequestDto requestDto, String token) {
		User user = userRepository.findByMyId(userId).orElseThrow(
			() -> new IllegalArgumentException("계정 정보가 없습니다.")
		);
		if (!passwordEncoder.matches(requestDto.getPassword(), user.getPassword())) {
			throw new IllegalArgumentException("비밀번호가 틀렸습니다.");
		}
		user.delete();
		//redis 비워주기
		//do 필터에서 파라마티로 url에 들어감 특정 url로 들어오면 접근 불가 가능
	}

	public void logout(Long userId, String token) {
		String tokenSet = token.substring(7);
		if (!jwtUtil.validateToken(tokenSet)) {
			throw new IllegalArgumentException("이미 로그아웃 되어 있습니다.");
		}
		jwtUtil.deleteRefreshToken(userId);
		Long expiration = jwtUtil.getExpiration(tokenSet);
		redisUtil.setBlackList(tokenSet, "access_token", expiration);
	}

	@Transactional(readOnly = true)
	public UserResponseDto getUser(Long userId) {
		User user = userRepository.findByMyId(userId).orElseThrow(
			() -> new IllegalArgumentException("계정 정보가 없습니다.")
		);
		return new UserResponseDto(user);
	}

	@Transactional(readOnly = true)
	public Page<UserResponseDto> getAllUsers() {
		return userRepository.findAllUser(PageRequest.of(0, 10)).orElseThrow(
			() -> new IllegalArgumentException("현재 가입된 유저가 없습니다.")
		);
	}

	public String findNickname(Long userId) {
		return userRepository.findNicknameById(userId)
			.orElseThrow(() -> new IllegalArgumentException("유저의 Nickname 이 존재하지 않습니다."));
	}

}
