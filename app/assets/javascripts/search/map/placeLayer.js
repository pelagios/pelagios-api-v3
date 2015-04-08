define(['search/events'], function(Events) {

  var MARKER_STYLE_SMALL = {
        color: '#a64a40',
        opacity: 1,
        fillColor: '#e75444',
        fillOpacity: 1,
        weight:1.5,
        radius:5,
        dropShadow:true
      },
      
      MARKER_STYLE_LARGE = (function(){
        var style = jQuery.extend({}, MARKER_STYLE_SMALL);
        style.radius = 9;
        return style;
      })(),
      
      RED_PIN = L.icon({
        iconUrl: 'leaf-green.png',
        shadowUrl: 'leaf-shadow.png',

        iconSize:     [38, 95], // size of the icon
        shadowSize:   [50, 64], // size of the shadow
        iconAnchor:   [22, 94], // point of the icon which will correspond to marker's location
        shadowAnchor: [4, 62],  // the same for the shadow
        popupAnchor:  [-3, -76] // point from which the popup should open relative to the iconAnchor
      });

  var PlaceLayer = function(map, eventBroker) {
    var isHidden = false,
    
        itemLayerGroup = L.layerGroup().addTo(map),
    
        placeLayerGroup = L.layerGroup().addTo(map),
    
        markers = {},
        
        showItem = function(item) {
          console.log(item);
          itemLayerGroup.clearLayers();
            var bounds =
                  [[item.geo_bounds.min_lat, item.geo_bounds.min_lon],
                   [item.geo_bounds.max_lat, item.geo_bounds.max_lon]],
                   
                centroidLat = (bounds[0][0] + bounds[1][0]) / 2,
                centroidLon = (bounds[0][1] + bounds[1][1]) / 2,
                
                isPoint = (bounds[0][0] === bounds[1][0] && bounds[0][1] === bounds[1][1]);

            if (isPoint) {
              L.circleMarker([ centroidLat, centroidLon ]).addTo(itemLayerGroup);
            } else {
              L.rectangle(bounds, {color: "#ff7800", weight: 1})
                .addTo(itemLayerGroup);
            }          
        },
        
        setItems = function(items) {
          itemLayerGroup.clearLayers();
          
          jQuery.each(items, function(idx, item) {
            /*
            var bounds =
                  [[item.geo_bounds.min_lat, item.geo_bounds.min_lon],
                   [item.geo_bounds.max_lat, item.geo_bounds.max_lon]],
                   
                centroidLat = (bounds[0][0] + bounds[1][0]) / 2,
                centroidLon = (bounds[0][1] + bounds[1][1]) / 2,
                
                isPoint = (bounds[0][0] === bounds[1][0] && bounds[0][1] === bounds[1][1]);

            if (isPoint) {
              L.circleMarker([ centroidLat, centroidLon ]).addTo(itemLayerGroup);
            } else {
              L.rectangle(bounds, {color: "#ff7800", weight: 1})
                .addTo(itemLayerGroup);
            }
            */
          });
          
        },
    
        setPlaces = function(places) {
          var uris = jQuery.map(places, function(p) { return p.gazetteer_uri });
          
          // Add places that are not already on the map
          jQuery.each(places, function(idx, place) {
            if (!markers[place.gazetteer_uri]) {
              markers[place.gazetteer_uri] = 
                L.circleMarker([ place.centroid_lat, place.centroid_lng ], MARKER_STYLE_SMALL)
                 .addTo(placeLayerGroup)
                 .on('click', function(e) {
                   L.marker([ place.centroid_lat, place.centroid_lng ]).addTo(map);
                   eventBroker.fireEvent(Events.SELECT_PLACE, place);
                 });
            }
          });
          
          // Now go through all markers and remove those that are not in the update
          jQuery.each(markers, function(uri, marker) {
            if (uris.indexOf(uri) < 0) {
              var icon = marker._icon,
                  shadow = marker._shadow;
              
              jQuery(icon).fadeOut(2000);
              jQuery(shadow).fadeOut(2000, function() {
                placeLayerGroup.removeLayer(marker);
              });

              delete markers[uri];
            }
          });
        },
        
        hide = function() {
          if (!isHidden) {
            map.removeLayer(placeLayerGroup);
            isHidden = true;
          }
        },
        
        show = function() {
          if (isHidden) {
            map.addLayer(placeLayerGroup);
            isHidden = false;
          }
        },
        
        clickNearest = function(latlng) {
          console.log(latlng);
        };

    this.setPlaces = setPlaces;
    this.setItems = setItems;
    this.showItem = showItem;
    this.show = show;
    this.hide = hide;
    this.clickNearest = clickNearest;
        
  };
  
  return PlaceLayer;

});
