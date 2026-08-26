package re.kr.icuh.drought.common.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Sha256Test {

    @DisplayName("hexOf()는 ASCII 문자열을 알려진 SHA-256 hex로 인코딩한다")
    @Test
    void hexOf_ASCII_문자열을_해싱한다() {
        String hex = Sha256.hexOf("test1234");

        assertThat(hex).isEqualTo("937e8d5fbb48bd4949536cd65b8d35c426b80d2f830c5c308e2cdec422ae2244");
    }

    @DisplayName("hexOf()는 한글(UTF-8 멀티바이트) 문자열을 알려진 SHA-256 hex로 인코딩한다")
    @Test
    void hexOf_한글_문자열을_해싱한다() {
        String hex = Sha256.hexOf("비밀번호1234");

        assertThat(hex).isEqualTo("a0191747a9c89b6b08303dc7f1497b4d34ebd44e978045eb0d5da4c04f04705f");
    }

    @DisplayName("hexOf()는 빈 문자열을 알려진 SHA-256 hex로 인코딩한다")
    @Test
    void hexOf_빈_문자열을_해싱한다() {
        String hex = Sha256.hexOf("");

        assertThat(hex).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @DisplayName("hexOf()의 출력은 항상 64자리다")
    @Test
    void hexOf_출력_길이는_항상_64자리다() {
        assertThat(Sha256.hexOf("test1234")).hasSize(64);
        assertThat(Sha256.hexOf("비밀번호1234")).hasSize(64);
        assertThat(Sha256.hexOf("")).hasSize(64);
    }

    @DisplayName("hexOf()의 출력은 항상 소문자 hex 문자로만 구성된다")
    @Test
    void hexOf_출력은_소문자_hex다() {
        assertThat(Sha256.hexOf("test1234")).matches("[0-9a-f]{64}");
        assertThat(Sha256.hexOf("비밀번호1234")).matches("[0-9a-f]{64}");
        assertThat(Sha256.hexOf("")).matches("[0-9a-f]{64}");
    }
}
