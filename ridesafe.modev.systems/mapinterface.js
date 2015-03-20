$(document).ready(function(){
        var map;  
        var ref;
    
        function goToError(){
            
            setTimeout(function(){
                $('body').addClass('fadeout');
            }, 3000);
            
            var errorId = document.getElementById("error");
            errorId.style.display = "block";

            var mapId = document.getElementById("map-canvas");
            mapId.style.display = "none"; 
            
            var splashId = document.getElementById("splash-wrapper");
            slpashId.style.display = "none";
            
        }
    

    function initialize(){

        // Get a reference to our posts

        var myLatlng = null;
        var prevLatlng = null;

        map = new google.maps.Map(document.getElementById('map-canvas'), {center: myLatlng, zoom: 17});

            ref.child(queryString).on("child_added", function(snapshot) {
            var gpsCord = snapshot.val();
            //create Latlng maps var using gpsCord String
            var resStr = gpsCord.split(" ");
            var lat=Number(resStr[0]);
            var lng=Number(resStr[1]);
            
           
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
        
        }//end init
        
        //queryStrig extraction and firebase child check
        var queryString = window.location.search;
        queryString = queryString.substring(1);
        if(queryString.length == 0){ 
            goToError();
        }
        else{
            ref = new Firebase("https://ridesafe.firebaseio.com/");
            
            ref.authAnonymously(function(error, authData) { 
                 if (error) {
                     window.alert("Authentication Error");
                     goToError();
                  } else {
                    var count=0;
                      
                    ref.child(queryString).once('value', function(snapshot) {
                    count++;
                    if (snapshot.val() === null) {
                        goToError();
                    } 
                    else {
                        //if we got a good child, create a map and load it up with points, fadeout of loading screen

                           google.maps.event.addDomListener(window, 'load', initialize());

                           google.maps.event.addListenerOnce(map, 'tilesloaded', function(){ $('body').addClass('fadeout'); });
                    }//end else

                    });//end firebae ref
                      
                      if(count==0){ //meaning it never found a child and thus never transitioned to a map
                         goToError();   
                        }

                  }//end auth else
            }, {
              remember: "sessionOnly"//delete user at broser close
            });
            

        }//end QS else

});//end doc ready