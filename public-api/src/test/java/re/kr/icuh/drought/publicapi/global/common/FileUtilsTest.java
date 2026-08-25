package re.kr.icuh.drought.publicapi.global.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class FileUtilsTest {

    private final FileUtils fileUtils = new FileUtils();

    @Nested
    @DisplayName("확장자 추출 (extractExtensionName)")
    class ExtractExtensionName {

        @DisplayName("파일명에서 확장자를 추출한다 (대소문자 보존, 마지막 점 기준)")
        @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
        @CsvSource({
                "report.pdf, pdf",
                "photo.JPEG, JPEG",
                "archive.tar.gz, gz"
        })
        void 확장자를_추출한다(String originName, String expected) {
            // when
            String extension = fileUtils.extractExtensionName(originName);

            // then
            assertThat(extension).isEqualTo(expected);
        }

        @Test
        @DisplayName("점이 없으면 빈 문자열을 반환한다")
        void 확장자가_없으면_빈_문자열() {
            // given
            String originName = "readme";

            // when
            String extension = fileUtils.extractExtensionName(originName);

            // then
            assertThat(extension).isEmpty();
        }

        @Test
        @DisplayName("점으로 끝나면 빈 문자열을 반환한다")
        void 점으로_끝나면_빈_문자열() {
            // given
            String originName = "trailing.";

            // when
            String extension = fileUtils.extractExtensionName(originName);

            // then
            assertThat(extension).isEmpty();
        }
    }

    @Nested
    @DisplayName("저장 파일명 생성 (createStoreFileName)")
    class CreateStoreFileName {

        @Test
        @DisplayName("'UUID.확장자' 형식으로 생성한다")
        void UUID와_확장자로_구성된다() {
            // given
            String originName = "report.pdf";

            // when
            String storedName = fileUtils.createStoreFileName(originName);

            // then
            assertThat(storedName)
                    .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.pdf");
        }

        @Test
        @DisplayName("같은 원본 이름이어도 호출마다 고유한 이름을 생성한다")
        void 호출마다_고유하다() {
            // given
            String originName = "report.pdf";

            // when
            String first = fileUtils.createStoreFileName(originName);
            String second = fileUtils.createStoreFileName(originName);

            // then
            assertThat(first).isNotEqualTo(second);
        }
    }
}
