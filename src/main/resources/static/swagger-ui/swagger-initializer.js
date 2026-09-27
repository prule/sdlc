window.ui = SwaggerUIBundle({
  url: "/api/v1/openapi/openapi.bundled.yaml",
  dom_id: "#swagger-ui",
  presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset],
  layout: "StandaloneLayout",
  validatorUrl: null,
});
