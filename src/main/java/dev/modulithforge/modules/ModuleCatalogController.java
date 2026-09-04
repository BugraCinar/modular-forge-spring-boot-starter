package dev.modulithforge.modules;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/modules")
public class ModuleCatalogController {

    private final ModuleCatalogService moduleCatalogService;

    public ModuleCatalogController(ModuleCatalogService moduleCatalogService) {
        this.moduleCatalogService = moduleCatalogService;
    }

    @GetMapping
    public List<ModuleDefinition> modules() {
        return moduleCatalogService.catalog();
    }
}
