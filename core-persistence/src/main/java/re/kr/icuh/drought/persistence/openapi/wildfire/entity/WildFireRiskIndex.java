package re.kr.icuh.drought.persistence.openapi.wildfire.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "drought_impact_wildfire_risk_index")
public class WildFireRiskIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDateTime analdate;
    private Integer area;
    private Integer d1;
    private Integer d2;
    private Integer d3;
    private Integer d4;
    private String doname;
    private Integer maxi;
    private Integer meanavg;
    private Integer mini;
    private String regioncode;
    private String sigucode;
    private String sigun;
    private Integer std;
    private Integer upplocalcd;

}
