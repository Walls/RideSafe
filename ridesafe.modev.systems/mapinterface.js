$(document).ready(function(){
        function goToError(){
            var errorId = document.getElementById("error");
            errorId.style.display = "block";

            var mapId = document.getElementById("map-canvas");
            mapId.style.display = "none";
            
            setTimeout(function(){
                $('body').addClass('fadeout');
            }, 3000);
            return;
            
        }
        var queryString = window.location.search;
        queryString = queryString.substring(1);
        if(queryString.length == 0){ 
            goToError();
        }
    
        firebaseString = "https://dazzling-torch-5228.firebaseio.com/"+queryString;
        var ref = new Firebase(firebaseString);
        ref.once('value', function(snapshot) {
        if (snapshot.val()==null) {
            goToError();
        }
        
        });
    var map;
    
    //var mapId = document.getElementById("map-canvas");
   // mapId.style.display = "none";
    function initialize(){

        // Get a reference to our posts

        var myLatlng = null;
        var prevLatlng = null;

        map = new google.maps.Map(document.getElementById('map-canvas'), {center: myLatlng, zoom: 17});

            ref.on("child_added", function(snapshot) {
            var gpsCord = snapshot.val();
            console.log(gpsCord);
            //create Latlng maps var using gpsCord String
            var resStr = gpsCord.split(" ");
            var lat=Number(resStr[0]);
            var lng=Number(resStr[1]);
            console.log(lat);
            console.log(lng);

            //connect the dots
            prevLatlng = myLatlng;
            myLatlng = new google.maps.LatLng(lat,lng);

            // Add to poly line and update/re-center Map
            if(prevLatlng!=null&&myLatlng!=null){
                var flightPath = new google.maps.Polyline({
                path: [prevLatlng,myLatlng],
                geodesic: true,
                strokeColor: '#003366',
                strokeOpacity: 1.0,
                strokeWeight: 2
              });

                flightPath.setMap(map);
                map.setCenter(myLatlng);

            }

        });
        
    }
    
    google.maps.event.addDomListener(window, 'load', initialize());
    
    google.maps.event.addListenerOnce(map, 'tilesloaded', function(){
        $('body').addClass('fadeout');
    });

    
});