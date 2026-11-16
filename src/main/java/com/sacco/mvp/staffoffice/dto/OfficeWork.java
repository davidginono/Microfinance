package com.sacco.mvp.staffoffice.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import com.sacco.mvp.staffoffice.model.StaffOffice;
import org.springframework.web.util.UriComponentsBuilder;

/** Small, read-only cross-workspace view. Null amounts mean no monetary total is available. */
public final class OfficeWork {
    private OfficeWork() {}
    public record Task(String number, String name, String stateKey, BigDecimal amount, String href) {}
    public record Page(List<Task> rows, int page, boolean hasNext) {
        public static Page empty(int page) { return new Page(List.of(),page,false); }
    }
    public record Source(String key, String labelKey, boolean chronological) {}
    public record Report(String labelKey, String href) {}
    public record Navigation(StaffOffice office,String source,String search,String sort,int page) {
        public Map<String,String> parameters() {
            return Map.of("office",office.getSlug(),"officeSource",source,"officeSearch",search,"officeSort",sort,"officePage",Integer.toString(page));
        }
        public Map<String,String> getParameters() { return parameters(); }
        public String href() {
            var url=UriComponentsBuilder.fromPath(office.getBase()+(source.equals("reports")?"/reports":"/queue"));
            if(!source.equals("reports"))url.queryParam("source",source).queryParam("search",search).queryParam("sort",sort).queryParam("page",page);
            return url.build().encode().toUriString();
        }
        public String getHref() { return href(); }
        public String labelKey() { return source.equals("reports")?"office.backReports":"office.back"; }
        public String getLabelKey() { return labelKey(); }
        public String attach(String path) {
            var url=UriComponentsBuilder.fromUriString(path);
            getParameters().forEach(url::replaceQueryParam);
            return url.build().encode().toUriString();
        }
    }
}
